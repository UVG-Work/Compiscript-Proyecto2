package com.uvg.compiscript.types;

import java.util.List;
import java.util.stream.Collectors;

public final class FunctionType extends Type {

    private final List<Type> parameterTypes;
    private final Type returnType;

    public FunctionType(List<Type> parameterTypes, Type returnType) {
        this.parameterTypes = List.copyOf(parameterTypes);
        this.returnType = returnType;
    }

    public List<Type> getParameterTypes() {
        return parameterTypes;
    }

    public Type getReturnType() {
        return returnType;
    }

    public int getArity() {
        return parameterTypes.size();
    }

    @Override
    public String getName() {
        return parameterTypes.stream()
                .map(Type::getName)
                .collect(Collectors.joining(", ", "(", ") => " + returnType.getName()));
    }

    @Override
    public int size() {
        return 8;
    }
}
