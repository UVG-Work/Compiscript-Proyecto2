package com.uvg.sql.ide;

import com.uvg.sql.diagnostics.Diagnostic;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

/** El editor de SQL, con resaltado, numeros de linea y las lineas con error marcadas. */
public class EditorPanel extends JPanel {

    private final RSyntaxTextArea area = new RSyntaxTextArea(24, 80);

    public EditorPanel() {
        super(new BorderLayout());
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        area.setTabSize(2);
        area.setTabsEmulated(true);
        area.setMarkOccurrences(true);
        area.setFont(IdeTheme.MONO);

        // El amarillo de fabrica para la linea del cursor compite con el rojo de
        // los errores; se baja a un neutro para que el color solo signifique error.
        area.setCurrentLineHighlightColor(new java.awt.Color(0xF1F4F8));
        area.setMarkOccurrencesColor(IdeTheme.ACCENT_SOFT);
        area.setSelectionColor(IdeTheme.ACCENT_SOFT);
        area.setCaretColor(IdeTheme.ACCENT);
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

    public String getSource() {
        return area.getText();
    }

    public void setSource(String text) {
        area.setText(text);
        area.setCaretPosition(0);
        area.discardAllEdits();
    }

    /** Pinta el fondo de las lineas con errores. */
    public void markErrors(List<Diagnostic> errors) {
        area.removeAllLineHighlights();
        for (Diagnostic error : errors) {
            try {
                area.addLineHighlight(error.line() - 1, IdeTheme.ERROR_TINT);
            } catch (BadLocationException ignored) {
                // El error apunta fuera del texto actual: no hay linea que pintar.
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
