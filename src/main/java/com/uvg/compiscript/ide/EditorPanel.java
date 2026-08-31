package com.uvg.compiscript.ide;

import com.uvg.compiscript.semantic.SemanticError;
import com.uvg.compiscript.semantic.SemanticError.Severity;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * El editor. Compiscript comparte palabras clave, cadenas y comentarios con
 * JavaScript, asi que su resaltador sirve tal cual y evita escribir un
 * {@code TokenMaker} propio.
 */
public class EditorPanel extends JPanel {

    private final RSyntaxTextArea area = new RSyntaxTextArea(24, 80);

    public EditorPanel() {
        super(new BorderLayout());
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        area.setCodeFoldingEnabled(true);
        area.setTabSize(2);
        area.setTabsEmulated(true);
        area.setMarkOccurrences(true);
        area.setFont(IdeTheme.MONO);

        // El amarillo de fabrica para la linea del cursor compite con el rojo de
        // los errores: dos significados peleando por el mismo canal. Se baja a
        // un neutro para que el color solo signifique diagnostico.
        area.setCurrentLineHighlightColor(new java.awt.Color(0xF1F4F8));
        area.setMarkOccurrencesColor(IdeTheme.ACCENT_SOFT);
        area.setSelectionColor(IdeTheme.ACCENT_SOFT);
        area.setCaretColor(IdeTheme.ACCENT);
        // Animar el emparejado de llaves es movimiento decorativo: no informa
        // de ningun estado y distrae mientras se escribe.
        area.setAnimateBracketMatching(false);

        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        scroll.setBorder(IdeTheme.hairline(0, 0, 0, 1));
        scroll.getGutter().setBorderColor(IdeTheme.LINE);
        scroll.getGutter().setBackground(IdeTheme.PANEL);
        scroll.getGutter().setLineNumberColor(IdeTheme.MUTED);
        scroll.getGutter().setLineNumberFont(IdeTheme.MONO_SMALL);
        add(scroll, BorderLayout.CENTER);
    }

    public RSyntaxTextArea getTextArea() {
        return area;
    }

    public String getSource() {
        return area.getText();
    }

    public void setSource(String text) {
        area.setText(text);
        area.setCaretPosition(0);
        area.discardAllEdits();
    }

    /** Pinta el fondo de las lineas con diagnosticos. */
    public void markDiagnostics(List<SemanticError> diagnostics) {
        area.removeAllLineHighlights();
        for (SemanticError diagnostic : diagnostics) {
            try {
                area.addLineHighlight(diagnostic.line() - 1,
                        diagnostic.severity() == Severity.ERROR
                                ? IdeTheme.ERROR_TINT : IdeTheme.WARN_TINT);
            } catch (BadLocationException ignored) {
                // Un diagnostico fuera del texto actual: no hay linea que pintar.
            }
        }
    }

    /** Lleva el cursor a una posicion; lo usa el doble clic en la tabla de errores. */
    public void goTo(int line, int column) {
        try {
            int start = area.getLineStartOffset(Math.max(0, line - 1));
            int offset = Math.min(start + Math.max(0, column), area.getDocument().getLength());
            area.setCaretPosition(offset);
            area.requestFocusInWindow();
        } catch (BadLocationException ignored) {
            // La posicion ya no existe porque el texto cambio.
        }
    }
}
