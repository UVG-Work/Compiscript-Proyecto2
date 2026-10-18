package com.uvg.compiscript.symbols;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Asigna a cada simbolo su lugar en memoria a partir del arbol de ambitos.
 *
 * <p>Los desplazamientos se cuentan por funcion, no por ambito: todos los
 * bloques de una funcion comparten su registro de activacion y van uno detras
 * de otro, sin solaparse. El codigo global y sus bloques van al area estatica G.
 */
public final class MemoryLayout {

    /** Enlace de control, enlace de acceso, direccion de retorno y valor de retorno. */
    public static final int FRAME_HEADER = 32;
    public static final int THIS_OFFSET = 32;
    public static final int WORD = 8;
    /** Las instancias empiezan con el puntero a la tabla de metodos; las listas, con su longitud. */
    public static final int OBJECT_HEADER = 8;
    public static final int ARRAY_HEADER = 8;
    public static final String MAIN = "main";

    /**
     * Toda etiqueta pasa por aqui. Las que inventa el compilador (main,
     * Clase.vtable, Clase.init) se reservan antes que las del usuario, y un
     * choque se resuelve con un sufijo $n: $ no puede aparecer en un
     * identificador, asi que el sufijo nunca vuelve a chocar.
     */
    private final Set<String> labels = new HashSet<>();

    private MemoryLayout() {
        labels.add(MAIN);
    }

    public static void assign(Scope global) {
        new MemoryLayout().place(global, "G", 0, "");
    }

    private String unique(String label) {
        String candidate = label;
        for (int n = 2; !labels.add(candidate); n++) {
            candidate = label + "$" + n;
        }
        return candidate;
    }

    private int place(Scope scope, String base, int offset, String labelPrefix) {
        for (Symbol symbol : scope.getSymbols()) {
            if (symbol instanceof FunctionSymbol function) {
                layoutFunction(function, labelPrefix + function.getName());
            } else if (symbol instanceof ClassSymbol type) {
                layoutClass(type);
            } else {
                symbol.setOffset(offset);
                symbol.setAddress(base + "[" + offset + "]");
                offset += symbol.getSize();
            }
        }
        for (Scope child : scope.getChildren()) {
            if (child.getKind() == Scope.Kind.BLOCK) {
                offset = place(child, base, offset, labelPrefix);
            }
        }
        return offset;
    }

    private void layoutFunction(FunctionSymbol function, String label) {
        function.setLabel(unique(label));
        Scope body = function.getBodyScope();
        if (body == null) {
            return;
        }
        int start = FRAME_HEADER + (function.isMethod() ? WORD : 0);
        int end = place(body, "fp", start, function.getLabel() + ".");
        function.setLocalsEnd(end);
        function.setFrameSize(end);
    }

    private void layoutClass(ClassSymbol type) {
        if (type.getInstanceSize() >= 0) {
            return;
        }
        type.setVtableLabel(unique(type.getName() + ".vtable"));
        type.setInitLabel(unique(type.getName() + ".init"));
        int offset = OBJECT_HEADER;
        List<FunctionSymbol> vtable = type.getVtable();
        if (type.getParent() != null) {
            layoutClass(type.getParent());
            offset = type.getParent().getInstanceSize();
            type.getFields().addAll(type.getParent().getFields());
            vtable.addAll(type.getParent().getVtable());
        }
        type.setAddress(type.getVtableLabel());
        if (type.getMemberScope() == null) {
            type.setInstanceSize(offset);
            return;
        }
        for (Symbol member : type.getMemberScope().getSymbols()) {
            if (member instanceof FunctionSymbol method) {
                layoutFunction(method, type.getName() + "." + method.getName());
                if (method.isConstructor()) {
                    continue;
                }
                int slot = type.slotOf(method.getName());
                if (slot >= 0) {
                    vtable.set(slot, method);
                } else {
                    vtable.add(method);
                }
            } else if (member instanceof VariableSymbol field) {
                field.setOffset(offset);
                field.setAddress("+" + offset);
                type.getFields().add(field);
                offset += field.getSize();
            }
        }
        type.setInstanceSize(offset);
    }
}
