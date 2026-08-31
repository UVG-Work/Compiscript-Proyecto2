package com.uvg.compiscript.symbols;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un entorno de la tabla de simbolos. Los ambitos no se destruyen al cerrarse:
 * quedan colgados de su padre para poder mostrar el estado de cada entorno al
 * terminar el analisis.
 */
public class Scope {

    public enum Kind { GLOBAL, FUNCTION, CLASS, BLOCK }

    private final String name;
    private final Kind kind;
    private final Scope parent;
    private final List<Scope> children = new ArrayList<>();
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private int size;

    public Scope(String name, Kind kind, Scope parent) {
        this.name = name;
        this.kind = kind;
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    /** @return false si el nombre ya estaba declarado en este mismo ambito. */
    public boolean define(Symbol symbol) {
        if (symbols.containsKey(symbol.getName())) {
            return false;
        }
        symbols.put(symbol.getName(), symbol);
        symbol.setScope(this);
        if (symbol.getType() != null) {
            symbol.setOffset(size);
            size += symbol.getType().size();
        }
        return true;
    }

    /** Busca solo en este ambito. */
    public Symbol resolveLocal(String name) {
        return symbols.get(name);
    }

    /** Busca en este ambito y sube por los padres. */
    public Symbol resolve(String name) {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            Symbol found = scope.symbols.get(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Devuelve el ambito donde vive el nombre, no el simbolo. Hace falta para
     *  decidir si un uso es una captura. */
    public Scope scopeOf(String name) {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            if (scope.symbols.containsKey(name)) {
                return scope;
            }
        }
        return null;
    }

    /** Profundidad de anidamiento: 0 es el ambito global. */
    public int getDepth() {
        int depth = 0;
        for (Scope scope = parent; scope != null; scope = scope.parent) {
            depth++;
        }
        return depth;
    }

    /** El ambito de funcion que contiene a este, o null si no hay ninguno. */
    public Scope enclosingFunction() {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            if (scope.kind == Kind.FUNCTION) {
                return scope;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public Scope getParent() {
        return parent;
    }

    public List<Scope> getChildren() {
        return children;
    }

    public Collection<Symbol> getSymbols() {
        return symbols.values();
    }

    /** Bytes ocupados por los simbolos declarados aqui. */
    public int getSize() {
        return size;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        dump(sb, 0);
        return sb.toString();
    }

    private void dump(StringBuilder sb, int depth) {
        String indent = "  ".repeat(depth);
        sb.append(String.format("%s[%s] %s (profundidad %d, %d bytes)%n",
                indent, kind, name, depth, size));
        if (symbols.isEmpty()) {
            sb.append(indent).append("  (vacio)").append(System.lineSeparator());
        }
        for (Symbol symbol : symbols.values()) {
            sb.append(String.format("%s  %-8s %-14s %-22s %-11s %d:%-3d alcance %d  offset %-4d%s%n",
                    indent, symbol.getKind(), symbol.getName(), symbol.getType(),
                    symbol.getTokenName(), symbol.getLine(), symbol.getColumn(),
                    symbol.getScopeDepth(), symbol.getOffset(), extras(symbol)));
        }
        for (Scope child : children) {
            child.dump(sb, depth + 1);
        }
    }

    private static String extras(Symbol symbol) {
        if (symbol instanceof FunctionSymbol function) {
            String extra = "  " + function.getArity() + " param";
            return function.getCaptures().isEmpty()
                    ? extra : extra + ", captura " + function.getCaptures();
        }
        if (symbol instanceof ParameterSymbol parameter) {
            return "  por " + parameter.getPassingMode().name().toLowerCase();
        }
        if (symbol instanceof VariableSymbol variable && variable.isCaptured()) {
            return "  capturada";
        }
        return "";
    }

    @Override
    public String toString() {
        return kind + " " + name;
    }
}
