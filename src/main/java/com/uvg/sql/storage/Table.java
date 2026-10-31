package com.uvg.sql.storage;

import com.uvg.sql.catalog.TableSchema;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Los registros de una tabla cargados en memoria.
 *
 * <p>Cada registro tiene un {@code rowid} estable: no cambia con UPDATE ni
 * cuando se borran otros registros. Es lo que guardan los indices, y por eso
 * un indice puede llevar directo a un registro sin recorrer la tabla.
 */
public final class Table {

    private final TableSchema schema;
    private final Map<Long, Object[]> rows = new LinkedHashMap<>();
    private long nextRowId = 1;

    Table(TableSchema schema) {
        this.schema = schema;
    }

    public TableSchema schema() {
        return schema;
    }

    public int size() {
        return rows.size();
    }

    public Set<Long> rowIds() {
        return rows.keySet();
    }

    public Collection<Object[]> rows() {
        return rows.values();
    }

    /** @return el registro, o null si no existe. No modificar el arreglo. */
    public Object[] row(long rowId) {
        return rows.get(rowId);
    }

    long insert(Object[] values) {
        long rowId = nextRowId++;
        rows.put(rowId, values);
        return rowId;
    }

    /** Al cargar del CSV se respeta el rowid guardado. */
    void load(long rowId, Object[] values) {
        rows.put(rowId, values);
        nextRowId = Math.max(nextRowId, rowId + 1);
    }

    void replace(long rowId, Object[] values) {
        rows.put(rowId, values);
    }

    void delete(long rowId) {
        rows.remove(rowId);
    }
}
