package com.uvg.compiscript.types;

public final class ArrayType extends Type {

    private final Type elementType;

    public ArrayType(Type elementType) {
        this.elementType = elementType;
    }

    public Type getElementType() {
        return elementType;
    }

    @Override
    public String getName() {
        return elementType.getName() + "[]";
    }

    @Override
    public int size() {
        return 8;
    }

    @Override
    public boolean isAssignableFrom(Type source) {
        if (source instanceof ErrorType || source == PrimitiveType.NULL) {
            return true;
        }
        // Invariantes: Perro[] no es asignable a Animal[].
        return source instanceof ArrayType other
                && elementType.equalsType(other.elementType);
    }
}
