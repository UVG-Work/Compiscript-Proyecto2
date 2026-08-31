package com.uvg.compiscript.ide;

import com.uvg.compiscript.tools.TreeExporter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Representacion visual del arbol de parseo.
 *
 * <p>Se usa {@link TreeViewer} de ANTLR, que dibuja el diagrama de nodos y
 * aristas dentro de la ventana. {@code grun -gui} no sirve aqui porque abre su
 * propia ventana y no funciona sin entorno grafico.
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
    private ParseTree tree;
    private Parser parser;
    private double scale = 1.0;

    public TreePanel() {
        super(new BorderLayout());

        JButton zoomOut = IdeTheme.button("−", "Alejar");
        JButton zoomIn = IdeTheme.button("+", "Acercar");
        JButton fit = IdeTheme.button("Ajustar", "Encajar el arbol completo en el panel");
        JButton actual = IdeTheme.button("1:1", "Tamano original");
        JButton export = IdeTheme.button("Exportar DOT/PNG",
                "Guardar el arbol como .dot y, si hay Graphviz, .png");

        zoomOut.addActionListener(e -> rescale(scale / 1.25));
        zoomIn.addActionListener(e -> rescale(scale * 1.25));
        fit.addActionListener(e -> fitToWidth());
        actual.addActionListener(e -> rescale(1.0));
        export.addActionListener(e -> export());

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
        bar.add(Box.createHorizontalStrut(IdeTheme.GAP));
        bar.add(export);

        deck.add(IdeTheme.emptyState("Sin arbol sintactico",
                "Compila con F5 para construirlo."), VACIO);
        deck.add(scroll, ARBOL);
        cards.show(deck, VACIO);

        add(bar, BorderLayout.NORTH);
        add(deck, BorderLayout.CENTER);
    }

    public void show(ParseTree tree, Parser parser) {
        this.tree = tree;
        this.parser = parser;
        viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
        viewer.setBoxColor(IdeTheme.SURFACE);
        viewer.setBorderColor(IdeTheme.LINE);
        viewer.setTextColor(IdeTheme.INK);
        scroll.setViewportView(viewer);
        nodeCount.setText(count(tree) + " nodos");
        cards.show(deck, ARBOL);
        // A escala 1 un programa real no cabe, pero encogerlo hasta que quepa lo
        // vuelve un borron ilegible. Se entra a tamano real y se desplaza la
        // vista hasta la raiz, que es por donde se empieza a leer.
        SwingUtilities.invokeLater(this::showRoot);
    }

    public void clear() {
        viewer = null;
        tree = null;
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
        // TreeLayout centra la raiz sobre sus hijos, asi que el centro horizontal
        // del lienzo es donde empieza el arbol.
        Dimension canvas = viewer.getPreferredSize();
        Dimension view = scroll.getViewport().getExtentSize();
        int x = Math.max(0, (canvas.width - view.width) / 2);
        scroll.getViewport().setViewPosition(new Point(x, 0));
    }

    /**
     * Ajusta solo al ancho y nunca por debajo del 15%: encoger tambien al alto
     * dejaba un arbol de miles de nodos en un borron de 7%.
     */
    private void fitToWidth() {
        if (viewer == null) {
            return;
        }
        viewer.setScale(1.0);
        Dimension full = viewer.getPreferredSize();
        Dimension view = scroll.getViewport().getExtentSize();
        if (full.width <= 0 || view.width <= 0) {
            rescale(1.0);
            return;
        }
        rescale(Math.max(0.15, Math.min(1.0, (double) view.width / full.width)));
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

    private void export() {
        if (tree == null) {
            JOptionPane.showMessageDialog(this, "Primero hay que compilar.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("arbol.dot"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dot = chooser.getSelectedFile().toPath();
        try {
            TreeExporter.writeDot(tree, parser, dot);
            String base = dot.toString().replaceFirst("\\.dot$", "");
            Path png = TreeExporter.renderPng(dot, Path.of(base + ".png"));
            JOptionPane.showMessageDialog(this, png != null
                    ? "Guardado:\n" + dot + "\n" + png
                    : "Guardado:\n" + dot + "\n\nGraphviz no esta instalado, no se genero el PNG.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int count(ParseTree node) {
        int total = 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            total += count(node.getChild(i));
        }
        return total;
    }
}
