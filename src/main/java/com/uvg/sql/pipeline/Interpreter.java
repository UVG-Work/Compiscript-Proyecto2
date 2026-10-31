package com.uvg.sql.pipeline;

import com.uvg.sql.ast.AstBuilder;
import com.uvg.sql.ast.AstView;
import com.uvg.sql.ast.Statement;
import com.uvg.sql.diagnostics.Diagnostic;
import com.uvg.sql.diagnostics.Diagnostic.Phase;
import com.uvg.sql.engine.ExecutionException;
import com.uvg.sql.engine.Executor;
import com.uvg.sql.pipeline.RunResult.Executed;
import com.uvg.sql.semantic.SemanticAnalyzer;
import com.uvg.sql.storage.Database;
import com.uvg.sql.storage.StorageException;
import java.util.ArrayList;
import java.util.List;

/**
 * Encadena las fases del enunciado:
 *
 * <pre>
 * SQL -> lexico -> sintactico -> AST -> semantico -> interpretacion -> archivos -> resultado
 * </pre>
 *
 * <p>Cada fase solo corre si la anterior no tuvo errores. El analisis
 * semantico revisa el script completo antes de ejecutar la primera sentencia;
 * en la ejecucion, un error detiene el script (las sentencias anteriores ya
 * quedaron guardadas, como en un cliente SQL en modo autocommit).
 */
public final class Interpreter {

    private Interpreter() {
    }

    /**
     * @param execute false para solo analizar (el boton "Analizar" del IDE):
     *                llega hasta el analisis semantico y no toca la base
     */
    public static RunResult run(String source, Database db, boolean execute) {
        ParseResult parse = Parsing.parse(source);
        List<Diagnostic> errors = new ArrayList<>(parse.errors());
        List<Executed> executed = new ArrayList<>();
        if (!parse.ok()) {
            return new RunResult(parse, List.of(), null, errors, executed);
        }

        AstBuilder builder = new AstBuilder();
        List<Statement> statements = builder.build(parse.tree());
        AstView ast = AstView.of(statements);
        errors.addAll(builder.errors());
        if (!errors.isEmpty()) {
            return new RunResult(parse, statements, ast, errors, executed);
        }

        SemanticAnalyzer analyzer = new SemanticAnalyzer(db.catalog());
        errors.addAll(analyzer.analyze(statements));
        if (!errors.isEmpty() || !execute) {
            return new RunResult(parse, statements, ast, errors, executed);
        }

        Executor executor = new Executor(db, analyzer.analysis());
        for (Statement statement : statements) {
            try {
                executed.add(new Executed(statement, executor.execute(statement)));
            } catch (ExecutionException e) {
                errors.add(new Diagnostic(Phase.EJECUCION, e.pos().line(), e.pos().column(),
                        e.getMessage()));
                break;
            } catch (StorageException e) {
                errors.add(new Diagnostic(Phase.EJECUCION, statement.pos().line(),
                        statement.pos().column(), "almacenamiento: " + e.getMessage()));
                break;
            }
        }
        return new RunResult(parse, statements, ast, errors, executed);
    }
}
