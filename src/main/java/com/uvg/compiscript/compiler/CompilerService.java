package com.uvg.compiscript.compiler;

import com.uvg.compiscript.SyntaxErrorListener;
import com.uvg.compiscript.parser.CompiscriptLexer;
import com.uvg.compiscript.parser.CompiscriptParser;
import com.uvg.compiscript.semantic.SemanticAnalyzer;
import com.uvg.compiscript.semantic.SemanticError;
import com.uvg.compiscript.semantic.SemanticError.Category;
import com.uvg.compiscript.semantic.SemanticError.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Las cuatro fases en un solo lugar: lexer, parser, recoleccion de errores de
 * sintaxis y analisis semantico.
 */
public final class CompilerService {

    private CompilerService() {
    }

    public static CompilationResult compileFile(Path file) throws IOException {
        return compile(Files.readString(file), file.toString());
    }

    public static CompilationResult compile(String source, String origin) {
        CompiscriptLexer lexer = new CompiscriptLexer(CharStreams.fromString(source, origin));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CompiscriptParser parser = new CompiscriptParser(tokens);

        // Recolectar los errores en vez de dejar que ANTLR los escriba a stderr
        // con otro formato.
        SyntaxErrorListener listener = new SyntaxErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        ParseTree tree = parser.program();
        tokens.fill();

        List<SemanticError> diagnostics = new ArrayList<>();
        for (SyntaxErrorListener.SyntaxError error : listener.getErrors()) {
            diagnostics.add(new SemanticError(error.line(), error.column(),
                    Category.SINTAXIS, Severity.ERROR, error.message()));
        }

        // Con el arbol mal formado el analisis semantico produce errores fantasma.
        if (listener.hasErrors()) {
            return new CompilationResult(origin, parser, tokens, tree,
                    List.copyOf(diagnostics), null, false);
        }

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(tree);
        diagnostics.addAll(analyzer.getDiagnostics());
        diagnostics.sort(Comparator.comparingInt(SemanticError::line)
                .thenComparingInt(SemanticError::column));

        return new CompilationResult(origin, parser, tokens, tree,
                List.copyOf(diagnostics), analyzer.getGlobalScope(), true);
    }
}
