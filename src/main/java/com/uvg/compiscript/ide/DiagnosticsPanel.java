package com.uvg.compiscript.ide;

import com.uvg.compiscript.semantic.SemanticError;
import com.uvg.compiscript.semantic.SemanticError.Severity;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Tabla de diagnosticos: sintaxis y semantica en la misma lista, cada uno con su
 * tipo y ubicacion. Doble clic salta a la linea en el editor.
 */
public class DiagnosticsPanel extends JPanel {

    private static final String VACIO = "vacio";
    private static final String TABLA = "tabla";

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Severidad", "Categoria", "Linea", "Col", "Mensaje"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);
    private final List<SemanticError> rows = new ArrayList<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final JPanel sinCompilar =
            IdeTheme.emptyState("Todavia no se ha compilado",
                    "Compila con F5 para ver aqui los errores y advertencias.");
    private final JPanel sinErrores =
            IdeTheme.emptyState("Sin errores ni advertencias",
                    "El programa pasa las 27 reglas semanticas del analizador.");

    public DiagnosticsPanel(BiConsumer<Integer, Integer> onActivate) {
        super(new BorderLayout());

        IdeTheme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        IdeTheme.widths(table, 90, 100, 60, 55, 900);

        // El color dice la severidad; el texto la repite para quien no lo distingue.
        table.setDefaultRenderer(Object.class, new IdeTheme.CellRenderer(false) {
            @Override
            protected Color foregroundFor(JTable source, int row, int column) {
                int index = source.convertRowIndexToModel(row);
                if (index < 0 || index >= rows.size()) {
                    return IdeTheme.INK;
                }
                return rows.get(index).severity() == Severity.ERROR
                        ? IdeTheme.ERROR : IdeTheme.WARN;
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() < 2) {
                    return;
                }
                int row = table.getSelectedRow();
                if (row >= 0) {
                    SemanticError diagnostic = rows.get(table.convertRowIndexToModel(row));
                    onActivate.accept(diagnostic.line(), diagnostic.column());
                }
            }
        });

        deck.add(sinCompilar, VACIO);
        deck.add(sinErrores, "ok");
        deck.add(IdeTheme.scroll(table), TABLA);
        cards.show(deck, VACIO);
        add(deck, BorderLayout.CENTER);
    }

    public void setDiagnostics(List<SemanticError> diagnostics) {
        rows.clear();
        rows.addAll(diagnostics);
        model.setRowCount(0);
        for (SemanticError diagnostic : diagnostics) {
            model.addRow(new Object[]{
                    diagnostic.severity() == Severity.ERROR ? "error" : "advertencia",
                    diagnostic.category(),
                    diagnostic.line(),
                    diagnostic.column(),
                    diagnostic.message()});
        }
        cards.show(deck, diagnostics.isEmpty() ? "ok" : TABLA);
    }

    public void reset() {
        rows.clear();
        model.setRowCount(0);
        cards.show(deck, VACIO);
    }
}
