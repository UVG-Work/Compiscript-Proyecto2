package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.Type;
import org.antlr.v4.runtime.Token;

public class VariableSymbol extends Symbol {

    private final boolean constant;
    private boolean initialized;
    private boolean captured;
    private boolean used;
    private int knownLength = -1;

    public VariableSymbol(String name, Type type, Token declaration, boolean constant) {
        super(name, type, declaration);
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

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    /** Longitud si se inicializo con un literal de arreglo; -1 si se desconoce. */
    public int getKnownLength() {
        return knownLength;
    }

    public void setKnownLength(int knownLength) {
        this.knownLength = knownLength;
    }

    @Override
    public String getKind() {
        return constant ? "const" : "var";
    }
}
