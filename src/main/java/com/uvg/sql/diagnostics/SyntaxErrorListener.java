package com.uvg.sql.diagnostics;

import com.uvg.sql.diagnostics.Diagnostic.Phase;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Recoge los errores del lexer y del parser en vez de dejar que ANTLR los
 * escriba a stderr. El mismo listener se registra en los dos: el tipo del
 * reconocedor dice si el error es lexico o sintactico.
 */
public class SyntaxErrorListener extends BaseErrorListener {

    private final List<Diagnostic> errors = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        Phase phase = recognizer instanceof Lexer ? Phase.LEXICO : Phase.SINTACTICO;
        // Una comilla sin cerrar hace que el lexer reporte todo el resto de
        // la linea como un solo caracter no reconocido; se dice que paso.
        String message = phase == Phase.LEXICO && msg.startsWith("token recognition error at: ''")
                ? "cadena de texto sin cerrar: falta la comilla ' final"
                : translate(msg);
        errors.add(new Diagnostic(phase, line, charPositionInLine, message));
    }

    public List<Diagnostic> errors() {
        return errors;
    }

    /**
     * Pasa al espanol los mensajes de ANTLR. Solo cambia la frase fija del
     * principio; los tokens y lo esperado se copian tal cual.
     */
    static String translate(String msg) {
        return msg
                .replace("token recognition error at:", "caracter no reconocido:")
                .replace("mismatched input", "entrada inesperada")
                .replace("extraneous input", "entrada sobrante")
                .replace("no viable alternative at input", "sentencia invalida cerca de")
                .replace("missing", "falta")
                .replace(" expecting ", " se esperaba ")
                .replace(" at ", " en ")
                .replace("<EOF>", "fin de archivo");
    }
}
