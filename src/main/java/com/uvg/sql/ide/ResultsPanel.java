package com.uvg.sql.ide;

import com.uvg.sql.engine.StatementResult;
import com.uvg.sql.pipeline.RunResult.Executed;
import com.uvg.sql.types.Values;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/**
 * Una pestana por sentencia ejecutada: el mensaje, el plan de acceso (si uso
 * un indice o recorrio la tabla) y, si es un SELECT, sus registros.
 */
public class ResultsPanel extends JPanel {

    private static final String VACIO = "vacio";
    private static final String TABS = "tabs";

    private final JTabbedPane tabs = new JTabbedPane();
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    public ResultsPanel() {
        super(new BorderLayout());
        tabs.setFont(IdeTheme.UI);
        deck.add(IdeTheme.emptyState("Sin resultados",
                "Ejecuta el script con F6 para ver sus resultados."), VACIO);
        deck.add(tabs, TABS);
        cards.show(deck, VACIO);
        add(deck, BorderLayout.CENTER);
    }

    public void setResults(List<Executed> executed) {
        tabs.removeAll();
        for (int i = 0; i < executed.size(); i++) {
            Executed e = executed.get(i);
            String kind = e.statement().getClass().getSimpleName();
            tabs.addTab((i + 1) + " " + kind, view(e.result()));
            tabs.setToolTipTextAt(i, kind + " de la linea " + e.statement().pos().line());
        }
        // Lo normal es querer ver el ultimo SELECT del script.
        for (int i = executed.size() - 1; i >= 0; i--) {
            if (executed.get(i).result().isQuery()) {
                tabs.setSelectedIndex(i);
                break;
            }
        }
        cards.show(deck, executed.isEmpty() ? VACIO : TABS);
    }

    private static JPanel view(StatementResult result) {
        JTextArea header = new JTextArea();
        header.setEditable(false);
        header.setFont(IdeTheme.MONO_SMALL);
        header.setBackground(IdeTheme.PANEL);
        header.setBorder(IdeTheme.padding(6, IdeTheme.GAP, 6, IdeTheme.GAP));
        StringBuilder text = new StringBuilder(result.summary());
        result.plan().forEach(line -> text.append("\nplan: ").append(line));
        header.setText(text.toString());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(header, BorderLayout.NORTH);
        if (result.isQuery()) {
            DefaultTableModel model = new DefaultTableModel(result.columns().toArray(), 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            for (Object[] row : result.rows()) {
                Object[] shown = new Object[row.length];
                for (int c = 0; c < row.length; c++) {
                    shown[c] = Values.display(row[c]);
                }
                model.addRow(shown);
            }
            JTable table = new JTable(model);
            IdeTheme.styleTable(table);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setDefaultRenderer(Object.class, new IdeTheme.CellRenderer(true));
            panel.add(IdeTheme.scroll(table), BorderLayout.CENTER);
        }
        return panel;
    }
}
