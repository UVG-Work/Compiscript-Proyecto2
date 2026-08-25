package com.uvg.compiscript.types;

public final class PrimitiveType extends Type {

    public static final PrimitiveType INTEGER = new PrimitiveType("integer", 4);
    public static final PrimitiveType FLOAT = new PrimitiveType("float", 8);
    public static final PrimitiveType STRING = new PrimitiveType("string", 8);
    public static final PrimitiveType BOOLEAN = new PrimitiveType("boolean", 1);
    public static final PrimitiveType NULL = new PrimitiveType("null", 8);
    public static final PrimitiveType VOID = new PrimitiveType("void", 0);

    private final String name;
    private final int size;

    private PrimitiveType(String name, int size) {
        this.name = name;
        this.size = size;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isAssignableFrom(Type source) {
        if (source instanceof ErrorType) {
            return true;
        }
        // integer promueve a float, nunca al reves.
        if (this == FLOAT && source == INTEGER) {
            return true;
        }
        return equalsType(source);
    }

    public boolean isNumeric() {
        return this == INTEGER || this == FLOAT;
    }
}
