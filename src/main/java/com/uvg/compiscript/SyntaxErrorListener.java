package com.uvg.compiscript;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Acumula los errores de sintaxis en una lista en vez de escribirlos a stderr,
 * para que tanto el CLI como el IDE puedan presentarlos a su manera.
 */
public class SyntaxErrorListener extends BaseErrorListener {

    public record SyntaxError(int line, int column, String message) {
        @Override
        public String toString() {
            return String.format("linea %d:%d %s", line, column, message);
        }
    }

    private final List<SyntaxError> errors = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        errors.add(new SyntaxError(line, charPositionInLine, msg));
    }

    public List<SyntaxError> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
