package com.uvg.compiscript.semantic;

public record SemanticError(int line, int column, Category category, Severity severity,
                            String message) {

    public enum Category {
        SINTAXIS, TIPOS, AMBITO, FUNCIONES, CONTROL, CLASES, LISTAS, GENERAL
    }

    public enum Severity {
        ERROR, ADVERTENCIA
    }

    @Override
    public String toString() {
        return String.format("linea %d:%d [%s/%s] %s",
                line, column, category, severity == Severity.ERROR ? "error" : "aviso", message);
    }
}
