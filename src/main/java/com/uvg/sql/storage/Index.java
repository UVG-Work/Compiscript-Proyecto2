package com.uvg.sql.storage;

import com.uvg.sql.catalog.IndexDef;
import com.uvg.sql.types.Values;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Un indice en memoria: de cada valor de la columna a los rowid que lo tienen.
 *
 * <p>Es un arbol ordenado ({@link TreeMap}), asi que sirve tanto para igualdad
 * como para rangos ({@code <, <=, >, >=}). Los NULL no se indexan: ninguna
 * comparacion con NULL es verdadera, asi que nunca se buscarian.
 */
public final class Index {

    /** Las comparaciones que se pueden responder con el indice. */
    public enum Lookup {
        EQ, LT, LTE, GT, GTE
    }

    private final IndexDef definition;
    private final int column;
    private final NavigableMap<Object, Set<Long>> entries = new TreeMap<>(Values::compare);

    Index(IndexDef definition, int column) {
        this.definition = definition;
        this.column = column;
    }

    public IndexDef definition() {
        return definition;
    }

    /** Posicion de la columna indexada dentro del registro. */
    public int column() {
        return column;
    }

    void add(Object value, long rowId) {
        if (value != null) {
            entries.computeIfAbsent(value, key -> new TreeSet<>()).add(rowId);
        }
    }

    void remove(Object value, long rowId) {
        if (value == null) {
            return;
        }
        Set<Long> ids = entries.get(value);
        if (ids != null) {
            ids.remove(rowId);
            if (ids.isEmpty()) {
                entries.remove(value);
            }
        }
    }

    /** @return los rowid cuyo valor cumple {@code columna <op> value}, en orden. */
    public Set<Long> find(Lookup op, Object value) {
        NavigableMap<Object, Set<Long>> range = switch (op) {
            case EQ -> entries.subMap(value, true, value, true);
            case LT -> entries.headMap(value, false);
            case LTE -> entries.headMap(value, true);
            case GT -> entries.tailMap(value, false);
            case GTE -> entries.tailMap(value, true);
        };
        Set<Long> ids = new TreeSet<>();
        range.values().forEach(ids::addAll);
        return ids;
    }

    NavigableMap<Object, Set<Long>> entries() {
        return entries;
    }
}
