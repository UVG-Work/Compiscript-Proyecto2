package com.uvg.compiscript.mips;

import com.uvg.compiscript.codegen.Instruction;
import com.uvg.compiscript.types.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una instruccion de TAC ya desarmada. El backend lee el mismo texto que
 * imprime {@code --tac}, asi que el codigo intermedio es de verdad la frontera
 * entre las dos mitades del compilador.
 *
 * <p>Campos segun la forma: {@code x = y op z}, {@code x = op y},
 * {@code x = y[z]}, {@code x[y] = z}, {@code if y op z goto label},
 * {@code x = call y, n}, {@code try label, y}.
 */
record Tac(Kind kind, String x, String y, String op, String z, String label, int n,
           Type type, Instruction source) {

    enum Kind {
        LABEL, COPY, BINARY, UNARY, TO_FLOAT, CONCAT, LOAD, STORE, ALLOC, ACCESS,
        GOTO, IF, IF_FALSE, PARAM, LINK, CALL, RETURN, PRINT, TRY, ENDTRY
    }

    private static final Pattern INDEXED = Pattern.compile("(t\\d+)\\[(.+)]");

    static Tac parse(Instruction instruction) {
        String text = instruction.text();
        Type type = instruction.type();
        if (instruction.isLabel()) {
            return of(Kind.LABEL, null, null, null, null, text.substring(0, text.length() - 1),
                    0, type, instruction);
        }
        List<String> t = tokens(text);
        switch (t.get(0)) {
            case "goto":
                return of(Kind.GOTO, null, null, null, null, t.get(1), 0, type, instruction);
            case "if", "ifFalse": {
                Kind kind = t.get(0).equals("if") ? Kind.IF : Kind.IF_FALSE;
                if (t.size() == 4) {
                    return of(kind, null, t.get(1), null, null, t.get(3), 0, type, instruction);
                }
                return of(kind, null, t.get(1), t.get(2), t.get(3), t.get(5), 0, type,
                        instruction);
            }
            case "param":
                return of(Kind.PARAM, null, t.get(1), null, null, null, 0, type, instruction);
            case "link":
                return of(Kind.LINK, null, t.get(1), null, null, null, 0, type, instruction);
            case "print":
                return of(Kind.PRINT, null, t.get(1), null, null, null, 0, type, instruction);
            case "return":
                return of(Kind.RETURN, null, t.size() > 1 ? t.get(1) : null, null, null, null, 0,
                        type, instruction);
            case "call":
                return of(Kind.CALL, null, t.get(1), null, null, null, Integer.parseInt(t.get(3)),
                        type, instruction);
            case "try":
                return of(Kind.TRY, null, t.get(3), null, null, t.get(1), 0, type, instruction);
            case "endtry":
                return of(Kind.ENDTRY, null, null, null, null, null, 0, type, instruction);
            default:
                return assignment(t, type, instruction);
        }
    }

    private static Tac assignment(List<String> t, Type type, Instruction instruction) {
        if (!"=".equals(t.get(1))) {
            throw new IllegalArgumentException("instruccion de TAC desconocida: " + instruction.text());
        }
        String target = t.get(0);
        List<String> rhs = t.subList(2, t.size());
        Matcher store = INDEXED.matcher(target);
        if (store.matches()) {
            return of(Kind.STORE, store.group(1), store.group(2), null, rhs.get(0), null, 0, type,
                    instruction);
        }
        String first = rhs.get(0);
        switch (first) {
            case "(float)":
                return of(Kind.TO_FLOAT, target, rhs.get(1), null, null, null, 0, type, instruction);
            case "alloc":
                return of(Kind.ALLOC, target, null, null, null, null, Integer.parseInt(rhs.get(1)),
                        type, instruction);
            case "access":
                return of(Kind.ACCESS, target, null, null, null, null, Integer.parseInt(rhs.get(1)),
                        type, instruction);
            case "concat":
                return of(Kind.CONCAT, target, rhs.get(1), null, rhs.get(3), null, 0, type,
                        instruction);
            case "call":
                return of(Kind.CALL, target, rhs.get(1), null, null, null,
                        Integer.parseInt(rhs.get(3)), type, instruction);
            default:
                break;
        }
        if (rhs.size() == 1) {
            Matcher load = INDEXED.matcher(first);
            if (load.matches()) {
                return of(Kind.LOAD, target, load.group(1), null, load.group(2), null, 0, type,
                        instruction);
            }
            return of(Kind.COPY, target, first, null, null, null, 0, type, instruction);
        }
        if (rhs.size() == 2) {
            return of(Kind.UNARY, target, rhs.get(1), first, null, null, 0, type, instruction);
        }
        return of(Kind.BINARY, target, first, rhs.get(1), rhs.get(2), null, 0, type, instruction);
    }

    private static Tac of(Kind kind, String x, String y, String op, String z, String label, int n,
                          Type type, Instruction source) {
        return new Tac(kind, x, y, op, z, label, n, type, source);
    }

    /** Separa por espacios y comas, sin partir una cadena entre comillas. */
    static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ') {
                i++;
            } else if (c == ',') {
                tokens.add(",");
                i++;
            } else if (c == '"') {
                int end = text.indexOf('"', i + 1);
                tokens.add(text.substring(i, end + 1));
                i = end + 1;
            } else {
                int start = i;
                while (i < text.length() && text.charAt(i) != ' ' && text.charAt(i) != ',') {
                    i++;
                }
                tokens.add(text.substring(start, i));
            }
        }
        return tokens;
    }

    /** Lo que la instruccion lee: temporales y variables, nunca constantes ni etiquetas. */
    List<String> uses() {
        List<String> names = new ArrayList<>();
        switch (kind) {
            case STORE -> {
                addName(names, x);
                addName(names, y);
                addName(names, z);
            }
            case LABEL, GOTO, ALLOC, ACCESS, ENDTRY, TRY -> {
            }
            default -> {
                addName(names, y);
                addName(names, z);
            }
        }
        return names;
    }

    /** Lo que la instruccion escribe, si escribe un nombre. */
    String def() {
        return kind == Kind.STORE ? null : x;
    }

    boolean endsBlock() {
        return kind == Kind.GOTO || kind == Kind.IF || kind == Kind.IF_FALSE
                || kind == Kind.RETURN;
    }

    private static void addName(List<String> names, String operand) {
        if (isName(operand)) {
            names.add(operand);
        }
    }

    static boolean isTemp(String operand) {
        return operand != null && operand.matches("t\\d+");
    }

    static boolean isVariable(String operand) {
        return operand != null && operand.matches("(G|fp)\\[\\d+]");
    }

    static boolean isName(String operand) {
        return isTemp(operand) || isVariable(operand);
    }
}
