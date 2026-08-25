package com.uvg.compiscript.types;

public abstract class Type {

    public abstract String getName();

    /** Tamano en bytes. Lo consume la generacion de codigo intermedio. */
    public abstract int size();

    public boolean equalsType(Type other) {
        return other != null && getName().equals(other.getName());
    }

    /** true si un valor de {@code source} puede guardarse en este tipo. */
    public boolean isAssignableFrom(Type source) {
        if (source instanceof ErrorType) {
            return true;
        }
        return equalsType(source);
    }

    @Override
    public String toString() {
        return getName();
    }
}
