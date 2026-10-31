package com.uvg.sql.pipeline;

import com.uvg.sql.diagnostics.SyntaxErrorListener;
import com.uvg.sql.parser.SqlLexer;
import com.uvg.sql.parser.SqlParser;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/** Fases 1 y 2: analisis lexico y sintactico con ANTLR. */
public final class Parsing {

    private Parsing() {
    }

    public static ParseResult parse(String source) {
        SyntaxErrorListener listener = new SyntaxErrorListener();

        SqlLexer lexer = new SqlLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SqlParser parser = new SqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        SqlParser.ScriptContext tree = parser.script();
        // El parser pide tokens solo hasta donde los necesita; se fuerza el
        // resto para que la vista de tokens siempre este completa.
        tokens.fill();
        return new ParseResult(tokens, parser, tree, List.copyOf(listener.errors()));
    }
}
