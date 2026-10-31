package com.uvg.sql.pipeline;

import com.uvg.sql.diagnostics.Diagnostic;
import com.uvg.sql.parser.SqlParser;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Lo que dejan el lexer y el parser: los tokens, el arbol de parseo y sus
 * errores. El arbol existe aunque haya errores (ANTLR se recupera), pero en
 * ese caso no se construye el AST ni se ejecuta nada.
 */
public record ParseResult(CommonTokenStream tokens,
                          SqlParser parser,
                          SqlParser.ScriptContext tree,
                          List<Diagnostic> errors) {

    public boolean ok() {
        return errors.isEmpty();
    }
}
