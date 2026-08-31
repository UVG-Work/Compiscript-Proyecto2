package com.uvg.compiscript.ide;

import com.uvg.compiscript.symbols.FunctionSymbol;
import com.uvg.compiscript.symbols.ParameterSymbol;
import com.uvg.compiscript.symbols.Scope;
import com.uvg.compiscript.symbols.Symbol;
import com.uvg.compiscript.symbols.VariableSymbol;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * La salida "estado de la tabla de simbolos por cada entorno": un arbol con los
 * ambitos anidados y, al seleccionar uno, sus simbolos con todos los atributos.
 */
public class SymbolTablePanel extends JPanel {

    private static final Icon GLOBAL = new IdeTheme.Dot(IdeTheme.ACCENT, false);
    private static final Icon CLASE = new IdeTheme.Dot(new Color(0x6F42C1), false);
    private static final Icon FUNCION = new IdeTheme.Dot(new Color(0x1A7F4B), false);
    private static final Icon BLOQUE = new IdeTheme.Dot(IdeTheme.MUTED, true);

    private static final String VACIO = "vacio";
    private static final String CONTENIDO = "contenido";

    private final DefaultTreeModel treeModel =
            new DefaultTreeModel(new DefaultMutableTreeNode("global"));
    private final JTree tree = new JTree(treeModel);

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Lexema", "Tipo semantico", "Tipo de dato", "Token",
                         "Alcance", "Offset", "Linea", "Col", "Detalle"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);
    private final JLabel caption = IdeTheme.headerLabel("Simbolos");
    private final JLabel frameSize = IdeTheme.muted("");
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    public SymbolTablePanel() {
        super(new BorderLayout());

        tree.setFont(IdeTheme.UI);
        tree.setRowHeight(21);
        tree.setBackground(IdeTheme.SURFACE);
        tree.setBorder(IdeTheme.padding(IdeTheme.GAP, IdeTheme.GAP, IdeTheme.GAP, 0));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new ScopeRenderer());
        tree.addTreeSelectionListener(event -> {
            Object node = tree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode selected
                    && selected.getUserObject() instanceof Scope scope) {
                showSymbolsOf(scope);
            }
        });

        IdeTheme.styleTable(table);
        table.setDefaultRenderer(Object.class, new IdeTheme.CellRenderer(false));
        IdeTheme.widths(table, 108, 88, 148, 74, 54, 52, 46, 38, 190);
        // El tipo de dato es una firma: se lee mucho mejor monoespaciado.
        table.getColumnModel().getColumn(2).setCellRenderer(new IdeTheme.CellRenderer(true));

        JPanel header = IdeTheme.strip();
        header.add(caption);
        header.add(Box.createHorizontalGlue());
        header.add(frameSize);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                IdeTheme.scroll(tree),
                IdeTheme.section(header, IdeTheme.scroll(table)));
        split.setDividerLocation(198);
        split.setResizeWeight(0.24);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(6);

        deck.add(IdeTheme.emptyState("Sin tabla de simbolos",
                "Compila un programa sin errores de sintaxis para construirla."), VACIO);
        deck.add(split, CONTENIDO);
        cards.show(deck, VACIO);
        add(deck, BorderLayout.CENTER);
    }

    public void setRoot(Scope global) {
        tableModel.setRowCount(0);
        if (global == null) {
            // Hubo errores de sintaxis: no hay tabla de simbolos que mostrar.
            treeModel.setRoot(new DefaultMutableTreeNode("global"));
            cards.show(deck, VACIO);
            return;
        }
        treeModel.setRoot(build(global));
        expandAll();
        tree.setSelectionRow(0);
        cards.show(deck, CONTENIDO);
    }

    private static DefaultMutableTreeNode build(Scope scope) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(scope);
        for (Scope child : scope.getChildren()) {
            node.add(build(child));
        }
        return node;
    }

    private void expandAll() {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    private void showSymbolsOf(Scope scope) {
        caption.setText(scope.getName());
        frameSize.setText(scope.getSymbols().size() + " simbolo(s) - marco de "
                + scope.getSize() + " bytes - profundidad " + scope.getDepth());
        tableModel.setRowCount(0);
        for (Symbol symbol : scope.getSymbols()) {
            tableModel.addRow(new Object[]{
                    symbol.getName(),
                    symbol.getKind(),
                    String.valueOf(symbol.getType()),
                    symbol.getTokenName(),
                    symbol.getScopeDepth(),
                    symbol.getOffset(),
                    symbol.getLine(),
                    symbol.getColumn(),
                    detail(symbol)});
        }
    }

    private static String detail(Symbol symbol) {
        if (symbol instanceof FunctionSymbol function) {
            String detail = function.getArity() + " parametro(s)";
            return function.getCaptures().isEmpty()
                    ? detail : detail + ", captura " + function.getCaptures();
        }
        if (symbol instanceof ParameterSymbol parameter) {
            return "paso por " + parameter.getPassingMode().name().toLowerCase()
                    + ", posicion " + parameter.getIndex();
        }
        if (symbol instanceof VariableSymbol variable) {
            String detail = variable.isInitialized() ? "inicializada" : "sin inicializar";
            return variable.isCaptured() ? detail + ", capturada" : detail;
        }
        return "";
    }

    /**
     * El nombre del ambito ya dice su clase ("clase Animal", "bloque#3"), asi que
     * anteponerle el enum era repetirlo. El tipo lo lleva el color del cuadrito y
     * el conteo de simbolos va en gris a la derecha.
     */
    private static final class ScopeRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree host, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(
                    host, value, selected, expanded, leaf, row, focused);
            setFont(IdeTheme.UI);
            setBackgroundNonSelectionColor(IdeTheme.SURFACE);
            setBackgroundSelectionColor(IdeTheme.ACCENT_SOFT);
            setTextSelectionColor(IdeTheme.INK);
            setTextNonSelectionColor(IdeTheme.INK);
            setBorderSelectionColor(null);

            Object user = value instanceof DefaultMutableTreeNode node
                    ? node.getUserObject() : value;
            if (user instanceof Scope scope) {
                setIcon(iconFor(scope));
                int symbols = scope.getSymbols().size();
                setText(symbols == 0 ? scope.getName()
                        : "<html>" + escape(scope.getName())
                                + " <font color='#8A9199'>" + symbols + "</font></html>");
            } else {
                setIcon(BLOQUE);
            }
            return this;
        }

        private static Icon iconFor(Scope scope) {
            return switch (scope.getKind()) {
                case GLOBAL -> GLOBAL;
                case CLASS -> CLASE;
                case FUNCTION -> FUNCION;
                case BLOCK -> BLOQUE;
            };
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
