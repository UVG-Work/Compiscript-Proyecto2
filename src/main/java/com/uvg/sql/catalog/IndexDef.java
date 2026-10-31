package com.uvg.sql.catalog;

/** Un indice declarado con {@code CREATE INDEX nombre ON tabla(columna)}. */
public record IndexDef(String name, String table, String column) {
}
