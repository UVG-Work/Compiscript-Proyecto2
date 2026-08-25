package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.Type;
import java.util.ArrayList;
import java.util.List;

public class FunctionSymbol extends Symbol {

    private final List<ParameterSymbol> parameters = new ArrayList<>();
    private Type returnType;
    private Scope bodyScope;
    private ClassSymbol owner;

    public FunctionSymbol(String name, int line, int column) {
        super(name, null, line, column);
    }

    public List<ParameterSymbol> getParameters() {
        return parameters;
    }

    public void addParameter(ParameterSymbol parameter) {
        parameters.add(parameter);
    }

    public Type getReturnType() {
        return returnType;
    }

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    public Scope getBodyScope() {
        return bodyScope;
    }

    public void setBodyScope(Scope bodyScope) {
        this.bodyScope = bodyScope;
    }

    /** La clase que la declara, o null si es una funcion libre. */
    public ClassSymbol getOwner() {
        return owner;
    }

    public void setOwner(ClassSymbol owner) {
        this.owner = owner;
    }

    public boolean isConstructor() {
        return owner != null && "constructor".equals(getName());
    }

    @Override
    public String getKind() {
        return owner == null ? "function" : "method";
    }
}
