package com.uvg.sql.ide;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.tree.Tree;

/**
 * Dibuja un arbol con el {@link TreeViewer} de ANTLR. Sirve para el arbol
 * sintactico (con los nombres de las reglas del parser) y para el AST, que
 * implementa la misma interfaz {@link Tree}.
 */
public class TreePanel extends JPanel {

    private static final String VACIO = "vacio";
    private static final String ARBOL = "arbol";

    private final JScrollPane scroll = IdeTheme.scroll(null);
    private final JLabel zoomLabel = IdeTheme.muted("");
    private final JLabel nodeCount = IdeTheme.muted("");
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    private TreeViewer viewer;
    private double scale = 1.0;

    public TreePanel(String emptyTitle) {
        super(new BorderLayout());

        JButton zoomOut = IdeTheme.button("−", "Alejar");
        JButton zoomIn = IdeTheme.button("+", "Acercar");
        JButton fit = IdeTheme.button("Ajustar", "Encajar el ancho del arbol en el panel");
        JButton actual = IdeTheme.button("1:1", "Tamano original");
        zoomOut.addActionListener(e -> rescale(scale / 1.25));
        zoomIn.addActionListener(e -> rescale(scale * 1.25));
        fit.addActionListener(e -> fitToWidth());
        actual.addActionListener(e -> rescale(1.0));

        JPanel bar = IdeTheme.strip();
        bar.add(zoomOut);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(zoomIn);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(fit);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(actual);
        bar.add(Box.createHorizontalStrut(IdeTheme.GAP));
        bar.add(zoomLabel);
        bar.add(Box.createHorizontalGlue());
        bar.add(nodeCount);

        deck.add(IdeTheme.emptyState(emptyTitle, "Analiza con F5 para construirlo."), VACIO);
        deck.add(scroll, ARBOL);
        cards.show(deck, VACIO);
        add(bar, BorderLayout.NORTH);
        add(deck, BorderLayout.CENTER);
    }

    /**
     * @param ruleNames los nombres de las reglas del parser para el arbol
     *                  sintactico; null para el AST, cuyos nodos ya traen su etiqueta
     */
    public void show(Tree tree, List<String> ruleNames) {
        if (tree == null) {
            clear();
            return;
        }
        viewer = new TreeViewer(ruleNames, tree) {
            // TreeViewer cambia los espacios por '·' dos veces: al medir la
            // caja (getText) y al dibujar el texto (text). Las etiquetas del
            // AST ("Tabla estudiantes AS e") se leen mejor con espacios.
            @Override
            protected String getText(Tree node) {
                return treeTextProvider.getText(node).replace("\n", "\\n");
            }

            @Override
            public void text(java.awt.Graphics g, String s, int x, int y) {
                g.drawString(s.replace("\n", "\\n"), x, y);
            }
        };
        viewer.setBoxColor(IdeTheme.SURFACE);
        viewer.setBorderColor(IdeTheme.LINE);
        viewer.setTextColor(IdeTheme.INK);
        scroll.setViewportView(viewer);
        nodeCount.setText(count(tree) + " nodos");
        cards.show(deck, ARBOL);
        // Se entra a tamano real con la raiz a la vista: encoger un arbol grande
        // hasta que quepa lo vuelve ilegible.
        SwingUtilities.invokeLater(this::showRoot);
    }

    public void clear() {
        viewer = null;
        scroll.setViewportView(null);
        nodeCount.setText("");
        zoomLabel.setText("");
        cards.show(deck, VACIO);
    }

    private void showRoot() {
        rescale(1.0);
        if (viewer == null) {
            return;
        }
        Dimension canvas = viewer.getPreferredSize();
        Dimension view = scroll.getViewport().getExtentSize();
        scroll.getViewport().setViewPosition(new Point(Math.max(0, (canvas.width - view.width) / 2), 0));
    }

    private void fitToWidth() {
        if (viewer == null) {
            return;
        }
        viewer.setScale(1.0);
        int full = viewer.getPreferredSize().width;
        int view = scroll.getViewport().getExtentSize().width;
        rescale(full <= 0 || view <= 0 ? 1.0 : Math.max(0.15, Math.min(1.0, (double) view / full)));
        scroll.getViewport().setViewPosition(new Point(0, 0));
    }

    private void rescale(double value) {
        scale = Math.max(0.05, Math.min(4.0, value));
        zoomLabel.setText(Math.round(scale * 100) + "%");
        if (viewer != null) {
            viewer.setScale(scale);
            scroll.getViewport().revalidate();
            scroll.repaint();
        }
    }

    private static int count(Tree node) {
        int total = 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            total += count(node.getChild(i));
        }
        return total;
    }
}
