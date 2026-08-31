package com.uvg.compiscript.semantic;

import com.uvg.compiscript.semantic.SemanticError.Category;
import com.uvg.compiscript.semantic.SemanticError.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Junta los diagnosticos del recorrido. El IDE consume la misma lista que el CLI.
 */
public class ErrorReporter {

    private final List<SemanticError> diagnostics = new ArrayList<>();

    public void error(ParserRuleContext ctx, Category category, String message) {
        at(ctx.getStart(), category, Severity.ERROR, message);
    }

    public void error(Token token, Category category, String message) {
        at(token, category, Severity.ERROR, message);
    }

    public void warning(ParserRuleContext ctx, Category category, String message) {
        at(ctx.getStart(), category, Severity.ADVERTENCIA, message);
    }

    public void warning(Token token, Category category, String message) {
        at(token, category, Severity.ADVERTENCIA, message);
    }

    public void warning(int line, int column, Category category, String message) {
        diagnostics.add(new SemanticError(line, column, category, Severity.ADVERTENCIA, message));
    }

    private void at(Token token, Category category, Severity severity, String message) {
        diagnostics.add(new SemanticError(token.getLine(), token.getCharPositionInLine(),
                category, severity, message));
    }

    /** Ordenados por posicion: es como los espera una tabla de errores. */
    public List<SemanticError> getDiagnostics() {
        List<SemanticError> sorted = new ArrayList<>(diagnostics);
        sorted.sort(Comparator.comparingInt(SemanticError::line)
                .thenComparingInt(SemanticError::column));
        return sorted;
    }

    public List<SemanticError> getErrors() {
        return getDiagnostics().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
    }

    public List<SemanticError> getWarnings() {
        return getDiagnostics().stream()
                .filter(d -> d.severity() == Severity.ADVERTENCIA)
                .toList();
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
