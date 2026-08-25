package com.uvg.compiscript.types;

/**
 * Tipo de una expresion que ya produjo un error. Absorbe cualquier operacion
 * posterior para que un solo error no genere una cascada de mensajes.
 */
public final class ErrorType extends Type {

    public static final ErrorType INSTANCE = new ErrorType();

    private ErrorType() {
    }

    @Override
    public String getName() {
        return "<error>";
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isAssignableFrom(Type source) {
        return true;
    }
}
