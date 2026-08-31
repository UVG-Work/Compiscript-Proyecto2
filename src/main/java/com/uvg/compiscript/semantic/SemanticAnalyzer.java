package com.uvg.compiscript.semantic;

import com.uvg.compiscript.symbols.Scope;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Punto de entrada del analisis. El CLI y el IDE consumen esta misma clase.
 */
public class SemanticAnalyzer {

    private final SemanticVisitor visitor = new SemanticVisitor();

    public void analyze(ParseTree tree) {
        visitor.visit(tree);
    }

    public List<SemanticError> getDiagnostics() {
        return visitor.getReporter().getDiagnostics();
    }

    public List<SemanticError> getErrors() {
        return visitor.getReporter().getErrors();
    }

    public List<SemanticError> getWarnings() {
        return visitor.getReporter().getWarnings();
    }

    public boolean hasErrors() {
        return visitor.getReporter().hasErrors();
    }

    /** Raiz del arbol de ambitos: el IDE lo pinta como JTree. */
    public Scope getGlobalScope() {
        return visitor.getScopes().getRoot();
    }

    public String dumpSymbolTable() {
        return getGlobalScope().dump();
    }
}
