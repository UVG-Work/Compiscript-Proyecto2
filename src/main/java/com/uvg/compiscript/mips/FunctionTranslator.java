package com.uvg.compiscript.mips;

import com.uvg.compiscript.codegen.FunctionCode;
import com.uvg.compiscript.codegen.Instruction;
import com.uvg.compiscript.symbols.FunctionSymbol;
import com.uvg.compiscript.symbols.MemoryLayout;
import com.uvg.compiscript.symbols.ParameterSymbol;
import com.uvg.compiscript.types.ArrayType;
import com.uvg.compiscript.types.PrimitiveType;
import com.uvg.compiscript.types.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce el TAC de una funcion a MIPS.
 *
 * <p>El registro de activacion es el mismo del TAC: {@code fp[n]} es
 * {@code n($fp)} y el marco ocupa {@code [$fp, $fp + tamano)}. Al final se
 * agregan las palabras donde la funcion guarda los {@code $s} que usa.
 *
 * <p>Secuencia de llamada: los cuatro primeros argumentos van en
 * {@code $a0-$a3} y el resto se apila; el enlace de acceso va en {@code $v1}.
 * {@code jal} deja la direccion de retorno en {@code $ra}. El prologo reserva
 * el marco bajando {@code $sp}, guarda ahi el enlace de control ({@code $fp}
 * de quien llamo), el de acceso y {@code $ra}, y copia los argumentos a sus
 * desplazamientos. El retorno deja el valor en {@code $v0} y en {@code fp[24]},
 * restaura los {@code $s}, {@code $ra}, {@code $fp} y {@code $sp} y salta a
 * {@code $ra}; quien llamo desapila los argumentos que apilo.
 */
final class FunctionTranslator {

    private static final int FIRST_ARGUMENT = MemoryLayout.FRAME_HEADER;
    private static final int RETURN_VALUE = 24;
    private static final int ACCESS_LINK = 8;
    private static final int RETURN_ADDRESS = 16;

    private final MipsGenerator program;
    private final FunctionCode function;
    private final List<Tac> code = new ArrayList<>();
    private final Liveness liveness;
    private final Asm body = new Asm();
    private final RegisterAllocator regs;
    private final Map<String, String> handlers = new HashMap<>();
    private final boolean hasTry;
    private final Set<String> pinned = new HashSet<>();
    private int params;
    private boolean linked;

    FunctionTranslator(MipsGenerator program, FunctionCode function) {
        this.program = program;
        this.function = function;
        for (Instruction instruction : function.code()) {
            Tac tac = Tac.parse(instruction);
            code.add(tac);
            if (tac.kind() == Tac.Kind.TRY) {
                handlers.put(tac.label(), tac.y());
            }
        }
        hasTry = !handlers.isEmpty();
        liveness = new Liveness(code);
        regs = new RegisterAllocator(body, this::home, liveness);
    }

    void translate(Asm out) {
        for (int i = 0; i < code.size(); i++) {
            startBlockIfNeeded(i);
            regs.at(i);
            pinned.clear();
            Tac tac = code.get(i);
            if (tac.kind() != Tac.Kind.LABEL) {
                Instruction source = tac.source();
                if (source.comment() != null) {
                    body.comment("--- " + source.comment());
                }
                body.comment(source.text());
            }
            translate(tac, i);
            if (Tac.isTemp(tac.def()) && !liveness.liveAfter(i).contains(tac.def())) {
                regs.release(tac.def());
            }
        }
        if (code.isEmpty() || !code.get(code.size() - 1).endsBlock()) {
            regs.writeBack(FunctionTranslator::isGlobal);
        }
        prologue(out);
        out.append(body);
        epilogue(out);
    }

    /** Al entrar a un bloque los registros se olvidan; si se llego cayendo, antes se guardan. */
    private void startBlockIfNeeded(int i) {
        if (i == 0 || !(code.get(i).kind() == Tac.Kind.LABEL || code.get(i - 1).endsBlock())) {
            return;
        }
        if (!code.get(i - 1).endsBlock()) {
            Set<String> live = liveness.liveAfter(i - 1);
            regs.writeBack(name -> Tac.isVariable(name) || live.contains(name));
        }
        regs.clear();
    }

    // ------------------------------------------------------------------
    // Prologo y epilogo
    // ------------------------------------------------------------------

    private int savedArea() {
        return regs.savedUsed().size() * 4;
    }

    private int mipsFrame() {
        return function.frameSize() + savedArea();
    }

    private void prologue(Asm out) {
        out.raw("");
        out.label(MipsGenerator.label(function.label()));
        out.op("addiu $sp, $sp, -" + mipsFrame(), "reserva el registro de activacion");
        out.op("sw $fp, 0($sp)", "enlace de control");
        out.op("sw $v1, " + ACCESS_LINK + "($sp)", "enlace de acceso");
        out.op("sw $ra, " + RETURN_ADDRESS + "($sp)", "direccion de retorno");
        out.op("move $fp, $sp");
        List<Integer> offsets = argumentOffsets();
        for (int k = 0; k < offsets.size(); k++) {
            int offset = offsets.get(k);
            if (k < 4) {
                out.op("sw $a" + k + ", " + offset + "($fp)", "argumento " + (k + 1));
            } else {
                int pushed = mipsFrame() + 4 * (offsets.size() - 1 - k);
                out.op("lw $t8, " + pushed + "($fp)", "argumento " + (k + 1) + ", en la pila");
                out.op("sw $t8, " + offset + "($fp)");
            }
        }
        int slot = function.frameSize();
        for (String reg : regs.savedUsed()) {
            out.op("sw " + reg + ", " + slot + "($fp)", "preserva " + reg);
            slot += 4;
        }
    }

    private void epilogue(Asm out) {
        out.label(returnLabel());
        int slot = function.frameSize();
        for (String reg : regs.savedUsed()) {
            out.op("lw " + reg + ", " + slot + "($fp)");
            slot += 4;
        }
        out.op("lw $ra, " + RETURN_ADDRESS + "($fp)");
        out.op("addiu $sp, $fp, " + mipsFrame(), "libera el registro de activacion");
        out.op("lw $fp, 0($fp)", "restaura el marco de quien llamo");
        out.op("jr $ra");
    }

    /** {@code this} va primero en un metodo; despues los parametros en orden. */
    private List<Integer> argumentOffsets() {
        List<Integer> offsets = new ArrayList<>();
        FunctionSymbol symbol = function.symbol();
        if (symbol == null) {
            return offsets;
        }
        if (symbol.isMethod()) {
            offsets.add(MemoryLayout.THIS_OFFSET);
        }
        for (ParameterSymbol parameter : symbol.getParameters()) {
            offsets.add(parameter.getOffset());
        }
        return offsets;
    }

    private String returnLabel() {
        return "ret" + MipsGenerator.label(function.label());
    }

    // ------------------------------------------------------------------
    // Operandos
    // ------------------------------------------------------------------

    /** Donde vive un nombre en memoria. */
    private String home(String name) {
        if (Tac.isTemp(name)) {
            int index = Integer.parseInt(name.substring(1)) - 1;
            return (function.tempsStart() + index * MemoryLayout.WORD) + "($fp)";
        }
        int offset = Integer.parseInt(name.substring(name.indexOf('[') + 1, name.length() - 1));
        return name.startsWith("G") ? "G+" + offset : offset + "($fp)";
    }

    /** Un operando en un registro; una constante se carga en {@code scratch}. */
    private String read(String operand, String scratch) {
        if (Tac.isName(operand)) {
            String reg = regs.getReg(operand, true, pinned);
            pinned.add(operand);
            return reg;
        }
        if (operand.equals("fp")) {
            return "$fp";
        }
        if (operand.startsWith("\"")) {
            body.op("la " + scratch + ", " + program.string(operand));
            return scratch;
        }
        Integer bits = constant(operand);
        if (bits == null) {
            body.op("la " + scratch + ", " + MipsGenerator.label(operand));
            return scratch;
        }
        if (bits == 0) {
            return "$zero";
        }
        body.op("li " + scratch + ", " + bits, operand);
        return scratch;
    }

    /** El valor de una constante como palabra; un float, por sus bits de precision simple. */
    private static Integer constant(String operand) {
        if (operand.equals("true")) {
            return 1;
        }
        if (operand.equals("false") || operand.equals("null")) {
            return 0;
        }
        if (operand.matches("-?\\d+")) {
            return new BigInteger(operand).intValue();
        }
        if (operand.matches("-?\\d+\\.\\d+")) {
            return Float.floatToIntBits(Float.parseFloat(operand));
        }
        return null;
    }

    /** Al retornar solo importan los globales: el marco de la funcion desaparece. */
    private static boolean isGlobal(String name) {
        return name.startsWith("G[");
    }

    private String write(String name) {
        String reg = regs.getReg(name, false, pinned);
        pinned.add(name);
        regs.markDirty(reg);
        return reg;
    }

    /** Suelta los temporales que la instruccion {@code i} leyo por ultima vez. */
    private void releaseDead(int i, String... operands) {
        for (String operand : operands) {
            if (Tac.isTemp(operand) && !liveness.liveAfter(i).contains(operand)) {
                regs.release(operand);
                pinned.remove(operand);
            }
        }
    }

    /** Antes de algo que puede fallar: el manejador del try lee la memoria, no los registros. */
    private void beforeFault() {
        if (hasTry) {
            regs.writeBack(name -> true);
        }
    }

    // ------------------------------------------------------------------
    // Instrucciones
    // ------------------------------------------------------------------

    private void translate(Tac tac, int i) {
        switch (tac.kind()) {
            case LABEL -> label(tac);
            case COPY -> copy(tac, i);
            case BINARY -> binary(tac, i);
            case UNARY -> unary(tac, i);
            case TO_FLOAT -> {
                String y = read(tac.y(), "$t8");
                releaseDead(i, tac.y());
                String x = write(tac.x());
                body.op("mtc1 " + y + ", $f0");
                body.op("cvt.s.w $f0, $f0");
                body.op("mfc1 " + x + ", $f0");
            }
            case CONCAT -> {
                String y = read(tac.y(), "$t8");
                String z = read(tac.z(), "$t9");
                body.op("move $a0, " + y);
                body.op("move $a1, " + z);
                body.op("jal rt_concat");
                releaseDead(i, tac.y(), tac.z());
                body.op("move " + write(tac.x()) + ", $v0");
            }
            case LOAD -> load(tac, i);
            case STORE -> store(tac, i);
            case ALLOC -> {
                body.op("li $a0, " + (tac.n() + 3) / 4 * 4);
                body.op("li $v0, 9", "sbrk: memoria en el heap");
                body.op("syscall");
                body.op("move " + write(tac.x()) + ", $v0");
            }
            case ACCESS -> {
                String x = write(tac.x());
                body.op("lw " + x + ", " + ACCESS_LINK + "($fp)");
                for (int k = 1; k < tac.n(); k++) {
                    body.op("lw " + x + ", " + ACCESS_LINK + "(" + x + ")");
                }
            }
            case GOTO -> {
                flushAt(i);
                body.op("j " + tac.label());
            }
            case IF, IF_FALSE -> branch(tac, i);
            case PARAM -> param(tac, i);
            case LINK -> {
                String y = read(tac.y(), "$v1");
                if (!y.equals("$v1")) {
                    body.op("move $v1, " + y);
                }
                releaseDead(i, tac.y());
                linked = true;
            }
            case CALL -> call(tac, i);
            case RETURN -> {
                if (tac.y() != null) {
                    String y = read(tac.y(), "$v0");
                    if (!y.equals("$v0")) {
                        body.op("move $v0, " + y);
                    }
                    body.op("sw $v0, " + RETURN_VALUE + "($fp)", "valor de retorno");
                }
                regs.writeBack(FunctionTranslator::isGlobal);
                body.op("j " + returnLabel());
            }
            case PRINT -> print(tac, i);
            case TRY -> {
                regs.writeBack(name -> true);
                body.op("lw $t8, rt_htop", "apila el manejador");
                body.op("la $t9, " + tac.label());
                body.op("sw $t9, 0($t8)");
                body.op("sw $sp, 4($t8)");
                body.op("sw $fp, 8($t8)");
                body.op("addiu $t8, $t8, 12");
                body.op("sw $t8, rt_htop");
            }
            case ENDTRY -> {
                body.op("lw $t8, rt_htop", "desapila el manejador");
                body.op("addiu $t8, $t8, -12");
                body.op("sw $t8, rt_htop");
            }
        }
    }

    private void label(Tac tac) {
        body.label(tac.label());
        String error = handlers.get(tac.label());
        if (error != null) {
            body.op("sw $v0, " + home(error), "el catch recibe el mensaje");
        }
    }

    private void flushAt(int i) {
        Set<String> live = liveness.liveAfter(i);
        regs.writeBack(name -> Tac.isVariable(name) || live.contains(name));
    }

    private void copy(Tac tac, int i) {
        if (Tac.isName(tac.y())) {
            String y = read(tac.y(), "$t8");
            releaseDead(i, tac.y());
            String x = write(tac.x());
            if (!x.equals(y)) {
                body.op("move " + x + ", " + y);
            }
            return;
        }
        String x = write(tac.x());
        String y = read(tac.y(), x);
        if (!y.equals(x)) {
            body.op("move " + x + ", " + y);
        }
    }

    private void binary(Tac tac, int i) {
        Type type = tac.type();
        String op = tac.op();
        if (type == PrimitiveType.FLOAT) {
            floatBinary(tac, i);
            return;
        }
        if (type == PrimitiveType.STRING) {
            String y = read(tac.y(), "$t8");
            String z = read(tac.z(), "$t9");
            body.op("move $a0, " + y);
            body.op("move $a1, " + z);
            body.op("jal rt_streq", "compara el contenido");
            releaseDead(i, tac.y(), tac.z());
            String x = write(tac.x());
            body.op((op.equals("==") ? "move " : "xori ") + x + ", $v0"
                    + (op.equals("==") ? "" : ", 1"));
            return;
        }
        Integer immediate = constant(tac.z());
        boolean small = immediate != null && immediate > -32768 && immediate < 32768;
        if ((op.equals("+") || op.equals("-")) && small) {
            String y = read(tac.y(), "$t8");
            releaseDead(i, tac.y());
            String x = write(tac.x());
            body.op("addiu " + x + ", " + y + ", " + (op.equals("+") ? immediate : -immediate));
            return;
        }
        String y = read(tac.y(), "$t8");
        String z = read(tac.z(), "$t9");
        if ((op.equals("/") || op.equals("%")) && (immediate == null || immediate == 0)) {
            beforeFault();
            body.op("beq " + z + ", $zero, rt_err_div");
        }
        releaseDead(i, tac.y(), tac.z());
        String x = write(tac.x());
        switch (op) {
            case "+" -> body.op("addu " + x + ", " + y + ", " + z);
            case "-" -> body.op("subu " + x + ", " + y + ", " + z);
            case "*" -> body.op("mul " + x + ", " + y + ", " + z);
            case "/" -> {
                body.op("div " + y + ", " + z);
                body.op("mflo " + x);
            }
            case "%" -> {
                body.op("div " + y + ", " + z);
                body.op("mfhi " + x);
            }
            case "<" -> body.op("slt " + x + ", " + y + ", " + z);
            case ">" -> body.op("slt " + x + ", " + z + ", " + y);
            case "<=" -> {
                body.op("slt " + x + ", " + z + ", " + y);
                body.op("xori " + x + ", " + x + ", 1");
            }
            case ">=" -> {
                body.op("slt " + x + ", " + y + ", " + z);
                body.op("xori " + x + ", " + x + ", 1");
            }
            case "==" -> {
                body.op("xor " + x + ", " + y + ", " + z);
                body.op("sltiu " + x + ", " + x + ", 1");
            }
            case "!=" -> {
                body.op("xor " + x + ", " + y + ", " + z);
                body.op("sltu " + x + ", $zero, " + x);
            }
            default -> throw new IllegalArgumentException("operador desconocido " + op);
        }
    }

    private void floatBinary(Tac tac, int i) {
        String y = read(tac.y(), "$t8");
        String z = read(tac.z(), "$t9");
        body.op("mtc1 " + y + ", $f0");
        body.op("mtc1 " + z + ", $f2");
        String op = tac.op();
        String arithmetic = switch (op) {
            case "+" -> "add.s";
            case "-" -> "sub.s";
            case "*" -> "mul.s";
            case "/" -> "div.s";
            default -> null;
        };
        if (arithmetic != null) {
            body.op(arithmetic + " $f0, $f0, $f2");
            releaseDead(i, tac.y(), tac.z());
            body.op("mfc1 " + write(tac.x()) + ", $f0");
            return;
        }
        boolean negated = floatCompare(op);
        releaseDead(i, tac.y(), tac.z());
        String x = write(tac.x());
        body.op("li " + x + ", 1");
        body.op((negated ? "movt " : "movf ") + x + ", $zero");
    }

    /**
     * Fija la bandera de la FPU comparando {@code $f0} con {@code $f2}. MIPS
     * solo tiene {@code ==}, {@code <} y {@code <=}: los demas invierten los
     * operandos o el resultado. Devuelve true si la bandera quedo invertida.
     */
    private boolean floatCompare(String op) {
        switch (op) {
            case "<" -> body.op("c.lt.s $f0, $f2");
            case "<=" -> body.op("c.le.s $f0, $f2");
            case ">" -> body.op("c.lt.s $f2, $f0");
            case ">=" -> body.op("c.le.s $f2, $f0");
            case "==", "!=" -> body.op("c.eq.s $f0, $f2");
            default -> throw new IllegalArgumentException("operador desconocido " + op);
        }
        return op.equals("!=");
    }

    private void unary(Tac tac, int i) {
        String y = read(tac.y(), "$t8");
        releaseDead(i, tac.y());
        String x = write(tac.x());
        if (tac.op().equals("!")) {
            body.op("xori " + x + ", " + y + ", 1");
        } else if (tac.type() == PrimitiveType.FLOAT) {
            body.op("mtc1 " + y + ", $f0");
            body.op("neg.s $f0, $f0");
            body.op("mfc1 " + x + ", $f0");
        } else {
            body.op("subu " + x + ", $zero, " + y);
        }
    }

    private void branch(Tac tac, int i) {
        boolean when = tac.kind() == Tac.Kind.IF;
        String y = read(tac.y(), "$t8");
        if (tac.op() == null) {
            flushAt(i);
            body.op((when ? "bne " : "beq ") + y + ", $zero, " + tac.label());
            return;
        }
        String z = read(tac.z(), "$t9");
        if (tac.type() == PrimitiveType.FLOAT) {
            body.op("mtc1 " + y + ", $f0");
            body.op("mtc1 " + z + ", $f2");
            boolean negated = floatCompare(tac.op());
            flushAt(i);
            body.op((when != negated ? "bc1t " : "bc1f ") + tac.label());
            return;
        }
        if (tac.type() == PrimitiveType.STRING) {
            body.op("move $a0, " + y);
            body.op("move $a1, " + z);
            body.op("jal rt_streq", "compara el contenido");
            flushAt(i);
            boolean equal = tac.op().equals("==") == when;
            body.op((equal ? "bne" : "beq") + " $v0, $zero, " + tac.label());
            return;
        }
        flushAt(i);
        String op = when ? tac.op() : negate(tac.op());
        String mnemonic = switch (op) {
            case "<" -> "blt";
            case "<=" -> "ble";
            case ">" -> "bgt";
            case ">=" -> "bge";
            case "==" -> "beq";
            default -> "bne";
        };
        body.op(mnemonic + " " + y + ", " + z + ", " + tac.label());
    }

    private static String negate(String op) {
        return switch (op) {
            case "<" -> ">=";
            case "<=" -> ">";
            case ">" -> "<=";
            case ">=" -> "<";
            case "==" -> "!=";
            default -> "==";
        };
    }

    /** Un acceso indexado revisa que la base no sea null y, si es una lista, el rango. */
    private void load(Tac tac, int i) {
        String base = read(tac.y(), "$t8");
        Integer offset = constant(tac.z());
        String index = offset == null ? read(tac.z(), "$t9") : null;
        checkAccess(base, index, offset, tac.type());
        releaseDead(i, tac.y(), tac.z());
        String x = write(tac.x());
        if (offset != null) {
            body.op("lw " + x + ", " + offset + "(" + base + ")");
        } else {
            body.op("addu $t8, " + base + ", " + index);
            body.op("lw " + x + ", 0($t8)");
        }
    }

    private void store(Tac tac, int i) {
        String base = read(tac.x(), "$t8");
        Integer offset = constant(tac.y());
        String index = offset == null ? read(tac.y(), "$t9") : null;
        checkAccess(base, index, offset, tac.type());
        String value = read(tac.z(), "$t9");
        if (offset != null) {
            body.op("sw " + value + ", " + offset + "(" + base + ")");
        } else {
            body.op("addu $t8, " + base + ", " + index);
            body.op("sw " + value + ", 0($t8)");
        }
        releaseDead(i, tac.x(), tac.y(), tac.z());
    }

    private void checkAccess(String base, String index, Integer offset, Type type) {
        beforeFault();
        body.op("beq " + base + ", $zero, rt_err_null");
        if (!(type instanceof ArrayType array)) {
            return;
        }
        int size = array.getElementType().size();
        body.op("lw $t8, 0(" + base + ")", "longitud de la lista");
        body.op("sll $t8, $t8, " + (size == 8 ? 3 : 2));
        body.op("addiu $t8, $t8, " + MemoryLayout.ARRAY_HEADER, "fin de la lista");
        if (index == null) {
            body.op("li $t9, " + offset);
            body.op("bgeu $t9, $t8, rt_err_index");
        } else {
            body.op("bgeu " + index + ", $t8, rt_err_index");
            body.op("sltiu $t8, " + index + ", " + MemoryLayout.ARRAY_HEADER);
            body.op("bne $t8, $zero, rt_err_index");
        }
    }

    private void param(Tac tac, int i) {
        int k = params++;
        if (k < 4) {
            String reg = "$a" + k;
            String y = read(tac.y(), reg);
            if (!y.equals(reg)) {
                body.op("move " + reg + ", " + y);
            }
        } else {
            String y = read(tac.y(), "$t8");
            body.op("addiu $sp, $sp, -4", "argumento " + (k + 1) + " a la pila");
            body.op("sw " + y + ", 0($sp)");
        }
        releaseDead(i, tac.y());
    }

    /**
     * Antes del salto se guardan los registros sucios que siguen vivos: la
     * funcion llamada usa los mismos {@code $t} y puede leer o escribir
     * globales y, por el enlace de acceso, las locales de esta funcion.
     */
    private void call(Tac tac, int i) {
        String target = Tac.isName(tac.y()) ? read(tac.y(), "$t8") : null;
        Set<String> live = liveness.liveAfter(i);
        regs.writeBack(name -> Tac.isVariable(name)
                || live.contains(name) && !name.equals(tac.x()));
        if (!linked) {
            body.op("move $v1, $zero", "sin enlace de acceso");
        }
        if (target == null) {
            body.op("jal " + MipsGenerator.label(tac.y()));
        } else {
            body.op("jalr " + target);
        }
        if (tac.n() > 4) {
            body.op("addiu $sp, $sp, " + 4 * (tac.n() - 4), "desapila los argumentos");
        }
        regs.clear();
        params = 0;
        linked = false;
        if (tac.x() != null) {
            body.op("move " + write(tac.x()) + ", $v0", "valor de retorno");
        }
    }

    private void print(Tac tac, int i) {
        String y = read(tac.y(), "$a0");
        Type type = tac.type();
        if (type == PrimitiveType.FLOAT) {
            body.op("mtc1 " + y + ", $f12");
            body.op("li $v0, 2");
            body.op("syscall");
        } else {
            if (!y.equals("$a0")) {
                body.op("move $a0, " + y);
            }
            if (type == PrimitiveType.INTEGER) {
                body.op("li $v0, 1");
                body.op("syscall");
            } else if (type == PrimitiveType.STRING) {
                body.op("jal rt_print_str");
            } else if (type == PrimitiveType.BOOLEAN) {
                body.op("jal rt_print_bool");
            } else {
                body.op("jal rt_print_ref");
            }
        }
        body.op("li $a0, 10");
        body.op("li $v0, 11", "salto de linea");
        body.op("syscall");
        releaseDead(i, tac.y());
    }
}
