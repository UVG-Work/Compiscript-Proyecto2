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
        sb.append(String.format("%s[%s] %s (%d bytes)%n", indent, kind, name, size));
        for (Symbol symbol : symbols.values()) {
            sb.append(String.format("%s  %-8s %-14s %-14s linea %d, offset %d%n",
                    indent, symbol.getKind(), symbol.getName(), symbol.getType(),
                    symbol.getLine(), symbol.getOffset()));
        }
        for (Scope child : children) {
            child.dump(sb, depth + 1);
        }
    }

    @Override
    public String toString() {
        return kind + " " + name;
    }
}
