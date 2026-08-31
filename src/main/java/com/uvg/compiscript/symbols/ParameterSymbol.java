package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.ArrayType;
import com.uvg.compiscript.types.ClassType;
import com.uvg.compiscript.types.Type;
import org.antlr.v4.runtime.Token;

public class ParameterSymbol extends Symbol {

    /** Metodo para paso de parametros. */
    public enum PassingMode { VALOR, REFERENCIA }

    private final int index;
    private final PassingMode passingMode;

    public ParameterSymbol(String name, Type type, Token declaration, int index) {
        super(name, type, declaration);
        this.index = index;
        // Los primitivos viajan por valor; clases y arreglos son referencias.
        this.passingMode = (type instanceof ClassType || type instanceof ArrayType)
                ? PassingMode.REFERENCIA : PassingMode.VALOR;
    }

    /** Posicion del parametro: la coincidencia de argumentos es posicional. */
    public int getIndex() {
        return index;
    }

    public PassingMode getPassingMode() {
        return passingMode;
    }

    @Override
    public String getKind() {
        return "param";
    }
}
