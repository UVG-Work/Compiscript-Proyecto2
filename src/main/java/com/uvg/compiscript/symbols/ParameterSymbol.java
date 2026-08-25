package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.Type;

public class ParameterSymbol extends Symbol {

    private final int index;

    public ParameterSymbol(String name, Type type, int line, int column, int index) {
        super(name, type, line, column);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String getKind() {
        return "param";
    }
}
