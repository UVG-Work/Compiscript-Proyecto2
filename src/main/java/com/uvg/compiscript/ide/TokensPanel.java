package com.uvg.compiscript.ide;

import com.uvg.compiscript.parser.CompiscriptLexer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/** El flujo de tokens del lexer: la primera fase, util al depurar la gramatica. */
public class TokensPanel extends JPanel {

    private static final String VACIO = "vacio";
    private static final String TABLA = "tabla";

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"#", "Token", "Lexema", "Linea", "Col"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    public TokensPanel() {
        super(new BorderLayout());
        IdeTheme.styleTable(table);
        table.setDefaultRenderer(Object.class, new IdeTheme.CellRenderer(false));
        // Token y lexema son texto del programa: monoespaciados se alinean.
        table.getColumnModel().getColumn(1).setCellRenderer(new IdeTheme.CellRenderer(true));
        table.getColumnModel().getColumn(2).setCellRenderer(new IdeTheme.CellRenderer(true));
        IdeTheme.widths(table, 55, 170, 220, 60, 50);

        deck.add(IdeTheme.emptyState("Sin tokens",
                "Compila con F5 para ver lo que reconocio el lexer."), VACIO);
        deck.add(IdeTheme.scroll(table), TABLA);
        cards.show(deck, VACIO);
        add(deck, BorderLayout.CENTER);
    }

    public void setTokens(CommonTokenStream tokens) {
        model.setRowCount(0);
        if (tokens == null) {
            cards.show(deck, VACIO);
            return;
        }
        int index = 0;
        for (Token token : tokens.getTokens()) {
            if (token.getType() == Token.EOF) {
                continue;
            }
            model.addRow(new Object[]{
                    index++,
                    CompiscriptLexer.VOCABULARY.getDisplayName(token.getType()),
                    token.getText(),
                    token.getLine(),
                    token.getCharPositionInLine()});
        }
        cards.show(deck, index == 0 ? VACIO : TABLA);
    }
}
