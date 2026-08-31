package com.uvg.compiscript.compiler;

import com.uvg.compiscript.semantic.SemanticError;
import com.uvg.compiscript.semantic.SemanticError.Severity;
import com.uvg.compiscript.symbols.Scope;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Todo lo que produce una compilacion, para que el CLI y el IDE consuman lo
 * mismo.
 *
 * <p>Los errores de sintaxis y los semanticos llegan en una sola lista
 * ({@code diagnostics}): el enunciado pide reportarlos "con su tipo y ubicacion"
 * y una tabla unica es lo que el IDE necesita.
 */
public record CompilationResult(String origin,
                                Parser parser,
                                CommonTokenStream tokens,
                                ParseTree tree,
                                List<SemanticError> diagnostics,
                                Scope globalScope,
                                boolean parsedCleanly) {

    public List<SemanticError> errors() {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).toList();
    }

    public List<SemanticError> warnings() {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ADVERTENCIA).toList();
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    /** Errores de sintaxis: los unicos que impiden llegar al analisis semantico. */
    public List<SemanticError> syntaxErrors() {
        return diagnostics.stream()
                .filter(d -> d.category() == SemanticError.Category.SINTAXIS)
                .toList();
    }
}
