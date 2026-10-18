package com.uvg.compiscript.codegen;

import com.uvg.compiscript.symbols.ClassSymbol;
import com.uvg.compiscript.symbols.FunctionSymbol;
import com.uvg.compiscript.symbols.MemoryLayout;
import com.uvg.compiscript.symbols.ParameterSymbol;
import com.uvg.compiscript.symbols.Scope;
import com.uvg.compiscript.symbols.Symbol;
import com.uvg.compiscript.symbols.VariableSymbol;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** El programa traducido: tablas de metodos y una funcion de TAC por cada funcion del fuente. */
public final class TacProgram {

    private static final int COMMENT_COLUMN = 36;

    private final List<FunctionCode> functions;
    private final List<ClassSymbol> classes;
    private final Scope global;

    TacProgram(List<FunctionCode> functions, List<ClassSymbol> classes, Scope global) {
        this.functions = functions;
        this.classes = classes;
        this.global = global;
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        for (ClassSymbol type : classes) {
            String methods = type.getVtable().stream().map(FunctionSymbol::getLabel)
                    .collect(Collectors.joining(", "));
            out.append("vtable ").append(type.getVtableLabel()).append(':')
                    .append(methods.isEmpty() ? "" : " " + methods).append('\n');
        }
        for (FunctionCode function : functions) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append("func ").append(function.label).append(", ")
                    .append(function.frameSize).append('\n');
            for (Instruction instruction : function.code) {
                out.append(line(instruction)).append('\n');
            }
            out.append("endfunc\n");
        }
        return out.toString();
    }

    private static String line(Instruction instruction) {
        if (instruction.isLabel()) {
            return instruction.text();
        }
        String text = "    " + instruction.text();
        if (instruction.comment() == null) {
            return text;
        }
        String padded = text.length() < COMMENT_COLUMN
                ? String.format("%-" + COMMENT_COLUMN + "s", text) : text + "  ";
        return padded + "; " + instruction.comment();
    }

    /** El area estatica, un registro de activacion por funcion y la forma de cada objeto. */
    public String recordsText() {
        StringBuilder out = new StringBuilder();
        List<Symbol> globals = storageOf(global);
        int globalSize = globals.stream().mapToInt(Symbol::getSize).sum();
        out.append("Area estatica G (").append(globalSize).append(" bytes)\n");
        globals.forEach(symbol -> row(out, symbol.getAddress(), symbol.getName(),
                String.valueOf(symbol.getType()), "global"));

        for (FunctionCode function : functions) {
            out.append('\n').append("Registro de activacion de ").append(function.label)
                    .append(" (").append(function.frameSize).append(" bytes)\n");
            row(out, "fp[0]", "", "", "enlace de control");
            row(out, "fp[8]", "", "", "enlace de acceso");
            row(out, "fp[16]", "", "", "direccion de retorno");
            row(out, "fp[24]", "", "", "valor de retorno");
            int tempsStart = MemoryLayout.FRAME_HEADER;
            FunctionSymbol symbol = function.symbol;
            if (symbol != null) {
                if (symbol.isMethod()) {
                    row(out, "fp[" + MemoryLayout.THIS_OFFSET + "]", "this",
                            symbol.getOwner().getName(), "objeto receptor");
                }
                for (Symbol local : storageOf(symbol.getBodyScope())) {
                    row(out, local.getAddress(), local.getName(), String.valueOf(local.getType()),
                            local instanceof ParameterSymbol ? "parametro" : "local");
                }
                tempsStart = symbol.getLocalsEnd();
            }
            for (int i = 0; i < function.temps.count(); i++) {
                row(out, "fp[" + (tempsStart + i * MemoryLayout.WORD) + "]", "t" + (i + 1), "",
                        "temporal");
            }
        }

        for (ClassSymbol type : classes) {
            out.append('\n').append("Objeto ").append(type.getName()).append(" (")
                    .append(type.getInstanceSize()).append(" bytes)\n");
            row(out, "+0", "", "", "tabla de metodos " + type.getVtableLabel());
            for (VariableSymbol field : type.getFields()) {
                row(out, field.getAddress(), field.getName(), String.valueOf(field.getType()),
                        "atributo de " + field.getScope().getName().replace("clase ", ""));
            }
            List<FunctionSymbol> vtable = type.getVtable();
            for (int slot = 0; slot < vtable.size(); slot++) {
                row(out, type.getVtableLabel() + "[" + slot * MemoryLayout.WORD + "]",
                        vtable.get(slot).getName(), "", vtable.get(slot).getLabel());
            }
        }
        return out.toString();
    }

    private static void row(StringBuilder out, String address, String name, String type,
                            String role) {
        out.append(String.format("  %-22s %-12s %-12s %s%n", address, name, type, role)
                .replaceAll(" +\n", "\n").replaceAll(" +\r\n", "\r\n"));
    }

    /** Lo que ocupa memoria en un ambito y sus bloques, sin entrar a funciones ni clases. */
    private static List<Symbol> storageOf(Scope scope) {
        List<Symbol> found = new ArrayList<>();
        if (scope != null) {
            collect(scope, found);
        }
        return found;
    }

    private static void collect(Scope scope, List<Symbol> found) {
        for (Symbol symbol : scope.getSymbols()) {
            if (!(symbol instanceof FunctionSymbol) && !(symbol instanceof ClassSymbol)) {
                found.add(symbol);
            }
        }
        for (Scope child : scope.getChildren()) {
            if (child.getKind() == Scope.Kind.BLOCK) {
                collect(child, found);
            }
        }
    }
}
