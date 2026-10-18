package com.uvg.compiscript.ide;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Texto monoespaciado de solo lectura: el codigo intermedio o los registros de activacion. */
public class TacPanel extends JPanel {

    private static final String VACIO = "vacio";
    private static final String TEXTO = "texto";

    private final JTextArea area = new JTextArea();
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    public TacPanel(String emptyTitle, String emptyHint) {
        super(new BorderLayout());
        area.setEditable(false);
        area.setFont(IdeTheme.MONO_SMALL);
        area.setForeground(IdeTheme.INK);
        area.setBorder(IdeTheme.padding(IdeTheme.GAP, IdeTheme.GAP, IdeTheme.GAP, IdeTheme.GAP));

        deck.add(IdeTheme.emptyState(emptyTitle, emptyHint), VACIO);
        deck.add(IdeTheme.scroll(area), TEXTO);
        cards.show(deck, VACIO);
        add(deck, BorderLayout.CENTER);
    }

    /** null deja el estado vacio: con errores no hay codigo intermedio que mostrar. */
    public void setText(String text) {
        area.setText(text == null ? "" : text);
        area.setCaretPosition(0);
        cards.show(deck, text == null ? VACIO : TEXTO);
    }
}
