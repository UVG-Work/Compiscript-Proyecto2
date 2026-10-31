package com.uvg.sql.engine;

import java.util.List;

/**
 * Resultado de una sentencia.
 *
 * @param summary una linea para el usuario ("3 fila(s) insertada(s) en ...")
 * @param columns encabezados del resultado; vacia si la sentencia no es SELECT
 * @param rows    registros del resultado de un SELECT
 * @param plan    como se accedio a cada tabla (indice o recorrido completo)
 */
public record StatementResult(String summary, List<String> columns, List<Object[]> rows,
                              List<String> plan) {

    public static StatementResult message(String summary) {
        return new StatementResult(summary, List.of(), List.of(), List.of());
    }

    public boolean isQuery() {
        return !columns.isEmpty();
    }
}
