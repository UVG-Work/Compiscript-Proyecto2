package com.uvg.sql.engine;

import com.uvg.sql.ast.Pos;

/**
 * Un error que solo se puede detectar al ejecutar: llave primaria duplicada,
 * division entre cero, subconsulta escalar con varios registros... Lleva la
 * posicion de la expresion o sentencia que lo provoco.
 */
public class ExecutionException extends RuntimeException {

    private final transient Pos pos;

    public ExecutionException(Pos pos, String message) {
        super(message);
        this.pos = pos;
    }

    public Pos pos() {
        return pos;
    }
}
