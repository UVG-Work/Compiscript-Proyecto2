package com.uvg.sql.pipeline;

import com.uvg.sql.ast.AstView;
import com.uvg.sql.ast.Statement;
import com.uvg.sql.diagnostics.Diagnostic;
import com.uvg.sql.engine.StatementResult;
import java.util.List;

/**
 * Todo lo que produjo una corrida, para que el CLI, el IDE y la bateria de
 * pruebas muestren lo mismo.
 *
 * @param parse      tokens y arbol sintactico (siempre presentes)
 * @param statements el AST; vacio si hubo errores lexicos o sintacticos
 * @param ast        la vista del AST; null si no se pudo construir
 * @param errors     los errores de todas las fases, en orden
 * @param executed   el resultado de cada sentencia ejecutada, en orden
 */
public record RunResult(ParseResult parse,
                        List<Statement> statements,
                        AstView ast,
                        List<Diagnostic> errors,
                        List<Executed> executed) {

    /** Una sentencia ejecutada con su resultado. */
    public record Executed(Statement statement, StatementResult result) {
    }

    public boolean ok() {
        return errors.isEmpty();
    }
}
