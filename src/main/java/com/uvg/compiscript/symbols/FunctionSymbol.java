package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

public class FunctionSymbol extends Symbol {

    private final List<ParameterSymbol> parameters = new ArrayList<>();
    private final Set<String> captures = new LinkedHashSet<>();
    private Type returnType;
    private Scope bodyScope;
    private ClassSymbol owner;
    private String label;
    private int localsEnd;
    private int frameSize;
    private int tempCount;

    public FunctionSymbol(String name, Token declaration) {
        super(name, null, declaration);
    }

    /** Numero de parametros. */
    public int getArity() {
        return parameters.size();
    }

    public List<ParameterSymbol> getParameters() {
        return parameters;
    }

    public void addParameter(ParameterSymbol parameter) {
        parameters.add(parameter);
    }

    /** Nombres que la funcion toma de un ambito de funcion exterior. No vacio = closure. */
    public Set<String> getCaptures() {
        return captures;
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
        bodyScope.setOwner(this);
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

    public boolean isMethod() {
        return owner != null;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
        setAddress(label);
    }

    /** Fin de parametros y locales en el marco; despues vienen los temporales. */
    public int getLocalsEnd() {
        return localsEnd;
    }

    public void setLocalsEnd(int localsEnd) {
        this.localsEnd = localsEnd;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public void setFrameSize(int frameSize) {
        this.frameSize = frameSize;
    }

    public int getTempCount() {
        return tempCount;
    }

    public void setTempCount(int tempCount) {
        this.tempCount = tempCount;
    }

    @Override
    public String getKind() {
        return owner == null ? "function" : "method";
    }
}
