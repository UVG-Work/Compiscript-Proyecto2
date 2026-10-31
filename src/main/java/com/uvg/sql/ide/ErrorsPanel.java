package com.uvg.sql.ide;

import com.uvg.sql.diagnostics.Diagnostic;
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
 * Los errores de todas las fases (lexico, sintactico, semantico, ejecucion)
 * en una tabla, cada uno con su ubicacion. Doble clic lleva a la linea.
 */
public class ErrorsPanel extends JPanel {

    private static final String VACIO = "vacio";
    private static final String OK = "ok";
    private static final String TABLA = "tabla";

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Fase", "Linea", "Col", "Mensaje"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);
    private final List<Diagnostic> rows = new ArrayList<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    public ErrorsPanel(BiConsumer<Integer, Integer> onActivate) {
        super(new BorderLayout());
        IdeTheme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        IdeTheme.widths(table, 100, 55, 45, 900);
        table.setDefaultRenderer(Object.class, new IdeTheme.CellRenderer(false) {
            @Override
            protected Color foregroundFor(JTable source, int row, int column) {
                return IdeTheme.ERROR;
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = table.getSelectedRow();
                if (event.getClickCount() == 2 && row >= 0) {
                    Diagnostic error = rows.get(row);
                    onActivate.accept(error.line(), error.column());
                }
            }
        });

        deck.add(IdeTheme.emptyState("Todavia no se ha analizado",
                "F5 analiza el script; F6 lo ejecuta."), VACIO);
        deck.add(IdeTheme.emptyState("Sin errores",
                "El script paso el analisis lexico, sintactico y semantico."), OK);
        deck.add(IdeTheme.scroll(table), TABLA);
        cards.show(deck, VACIO);
        add(deck, BorderLayout.CENTER);
    }

    public void setErrors(List<Diagnostic> errors) {
        rows.clear();
        rows.addAll(errors);
        model.setRowCount(0);
        for (Diagnostic error : errors) {
            model.addRow(new Object[]{error.phase(), error.line(), error.column(), error.message()});
        }
        cards.show(deck, errors.isEmpty() ? OK : TABLA);
    }
}
