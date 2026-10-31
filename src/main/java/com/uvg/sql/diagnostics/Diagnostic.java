package com.uvg.sql.diagnostics;

/**
 * Un error con su fase y su ubicacion en el texto SQL.
 *
 * <p>Todas las fases (lexer, parser, analisis semantico y ejecucion) producen
 * este mismo tipo, asi el CLI, el IDE y la bateria de pruebas los presentan
 * igual. La columna empieza en 0, como la entrega ANTLR.
 */
public record Diagnostic(Phase phase, int line, int column, String message) {

    public enum Phase {
        LEXICO, SINTACTICO, SEMANTICO, EJECUCION
    }

    @Override
    public String toString() {
        return String.format("[%s] linea %d:%d %s", phase, line, column, message);
    }
}
