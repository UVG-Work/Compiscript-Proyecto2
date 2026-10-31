package com.uvg.sql.ast;

/**
 * Un nombre de tabla, columna, alias o indice, ya en minusculas, con la
 * posicion donde aparece para que los errores senalen el nombre exacto.
 */
public record Ident(String name, Pos pos) {

    @Override
    public String toString() {
        return name;
    }
}
