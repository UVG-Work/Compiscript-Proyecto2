package com.uvg.sql.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las tablas y los indices de una base de datos: lo que guarda metadata.txt.
 *
 * <p>El analisis semantico trabaja sobre una {@link #copy() copia}: aplica a
 * ella los CREATE y DROP del script para que una sentencia pueda usar la tabla
 * que creo la anterior, sin tocar la base real antes de ejecutar.
 */
public final class Catalog {

    private final Map<String, TableSchema> tables = new LinkedHashMap<>();
    private final Map<String, IndexDef> indexes = new LinkedHashMap<>();

    public Catalog copy() {
        Catalog copy = new Catalog();
        copy.tables.putAll(tables);
        copy.indexes.putAll(indexes);
        return copy;
    }

    // ------------------------------------------------------------------ tablas

    /** @return el esquema, o null si la tabla no existe. */
    public TableSchema table(String name) {
        return tables.get(name);
    }

    public List<TableSchema> tables() {
        return List.copyOf(tables.values());
    }

    public void addTable(TableSchema schema) {
        tables.put(schema.name(), schema);
    }

    /** Quita la tabla y, con ella, sus indices. */
    public void removeTable(String name) {
        tables.remove(name);
        indexes.values().removeIf(index -> index.table().equals(name));
    }

    // ----------------------------------------------------------------- indices

    /** @return el indice, o null si no existe. */
    public IndexDef index(String name) {
        return indexes.get(name);
    }

    public List<IndexDef> indexes() {
        return List.copyOf(indexes.values());
    }

    public List<IndexDef> indexesOf(String table) {
        List<IndexDef> result = new ArrayList<>();
        for (IndexDef index : indexes.values()) {
            if (index.table().equals(table)) {
                result.add(index);
            }
        }
        return result;
    }

    /** @return el indice sobre esa columna, o null si no tiene. */
    public IndexDef indexOn(String table, String column) {
        for (IndexDef index : indexes.values()) {
            if (index.table().equals(table) && index.column().equals(column)) {
                return index;
            }
        }
        return null;
    }

    public void addIndex(IndexDef index) {
        indexes.put(index.name(), index);
    }

    public void removeIndex(String name) {
        indexes.remove(name);
    }
}
