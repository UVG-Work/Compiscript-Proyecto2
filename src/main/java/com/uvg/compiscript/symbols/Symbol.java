package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.Type;

public abstract class Symbol {

    private final String name;
    private final int line;
    private final int column;
    private Type type;
    private int offset = -1;

    protected Symbol(String name, Type type, int line, int column) {
        this.name = name;
        this.type = type;
        this.line = line;
        this.column = column;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /** Desplazamiento dentro de su ambito, asignado por {@link Scope#define}. */
    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    /** Etiqueta corta para la tabla de simbolos del IDE. */
    public abstract String getKind();

    @Override
    public String toString() {
        return String.format("%s %s : %s", getKind(), name, type);
    }
}
