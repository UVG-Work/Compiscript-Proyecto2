package com.uvg.sql.catalog;

import java.util.List;

/** Esquema de una tabla: su nombre y sus columnas en orden. */
public record TableSchema(String name, List<Column> columns) {

    public TableSchema {
        columns = List.copyOf(columns);
    }

    /** @return la posicion de la columna, o -1 si la tabla no la tiene. */
    public int indexOf(String column) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equals(column)) {
                return i;
            }
        }
        return -1;
    }

    public Column column(int index) {
        return columns.get(index);
    }

    /** @return la posicion de la llave primaria, o -1 si la tabla no tiene. */
    public int primaryKeyIndex() {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).primaryKey()) {
                return i;
            }
        }
        return -1;
    }
}
