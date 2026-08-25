package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.Type;

public class VariableSymbol extends Symbol {

    private final boolean constant;
    private boolean initialized;
    private boolean captured;

    public VariableSymbol(String name, Type type, int line, int column, boolean constant) {
        super(name, type, line, column);
        this.constant = constant;
    }

    public boolean isConstant() {
        return constant;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /** true si una funcion anidada la usa: es lo que convierte la funcion en closure. */
    public boolean isCaptured() {
        return captured;
    }

    public void setCaptured(boolean captured) {
        this.captured = captured;
    }

    @Override
    public String getKind() {
        return constant ? "const" : "var";
    }
}
