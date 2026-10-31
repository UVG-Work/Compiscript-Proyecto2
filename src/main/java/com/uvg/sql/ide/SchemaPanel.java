package com.uvg.sql.ide;

import com.uvg.sql.catalog.Column;
import com.uvg.sql.catalog.IndexDef;
import com.uvg.sql.catalog.TableSchema;
import com.uvg.sql.storage.Database;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * El esquema de la base abierta, tal como esta en metadata.txt: cada tabla con
 * su numero de registros, sus columnas, tipos, restricciones e indices.
 */
public class SchemaPanel extends JPanel {

    private static final String VACIO = "vacio";
    private static final String TABLA = "tabla";

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Tabla", "Columna", "Tipo", "Restricciones", "Indice"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);

    public SchemaPanel() {
        super(new BorderLayout());
        IdeTheme.styleTable(table);
        table.setDefaultRenderer(Object.class, new IdeTheme.CellRenderer(true));
        IdeTheme.widths(table, 240, 150, 120, 140, 140);
        deck.add(IdeTheme.emptyState("La base no tiene tablas",
                "Crea una con CREATE TABLE y ejecuta con F6."), VACIO);
        deck.add(IdeTheme.scroll(table), TABLA);
        cards.show(deck, VACIO);
        add(deck, BorderLayout.CENTER);
    }

    public void show(Database db) {
        model.setRowCount(0);
        for (TableSchema schema : db.catalog().tables()) {
            String title = schema.name() + " (" + db.table(schema.name()).size() + " registros)";
            for (int i = 0; i < schema.columns().size(); i++) {
                Column column = schema.column(i);
                String constraints = column.primaryKey() ? "PRIMARY KEY"
                        : column.notNull() ? "NOT NULL" : "";
                IndexDef index = db.catalog().indexOn(schema.name(), column.name());
                model.addRow(new Object[]{i == 0 ? title : "", column.name(), column.type(),
                        constraints, index == null ? "" : index.name()});
            }
        }
        cards.show(deck, model.getRowCount() == 0 ? VACIO : TABLA);
    }
}
