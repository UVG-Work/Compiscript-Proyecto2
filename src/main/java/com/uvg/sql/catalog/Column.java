package com.uvg.sql.catalog;

import com.uvg.sql.types.DataType;

/** Una columna del esquema. Una llave primaria es implicitamente NOT NULL. */
public record Column(String name, DataType type, boolean primaryKey, boolean notNull) {

    public boolean acceptsNull() {
        return !primaryKey && !notNull;
    }
}
