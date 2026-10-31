package com.uvg.sql.types;

/**
 * Tipo de una columna o de una expresion.
 *
 * <p>{@code length} solo tiene sentido en VARCHAR (la n de VARCHAR(n)); en los
 * demas es 0. {@link Kind#NULL} es el tipo del literal NULL: compatible con
 * cualquier otro, nunca el tipo de una columna.
 */
public record DataType(Kind kind, int length) {

    public enum Kind {
        INT, FLOAT, VARCHAR, BOOLEAN, DATE, NULL
    }

    public static final DataType INT = new DataType(Kind.INT, 0);
    public static final DataType FLOAT = new DataType(Kind.FLOAT, 0);
    public static final DataType BOOLEAN = new DataType(Kind.BOOLEAN, 0);
    public static final DataType DATE = new DataType(Kind.DATE, 0);
    public static final DataType NULL = new DataType(Kind.NULL, 0);

    public static DataType varchar(int length) {
        return new DataType(Kind.VARCHAR, length);
    }

    public boolean isNumeric() {
        return kind == Kind.INT || kind == Kind.FLOAT;
    }

    public boolean isNull() {
        return kind == Kind.NULL;
    }

    /** Como se escribe en SQL y en metadata.txt: INT, VARCHAR(50)... */
    @Override
    public String toString() {
        return kind == Kind.VARCHAR ? "VARCHAR(" + length + ")" : kind.name();
    }

    /** Inverso de {@link #toString()}; lo usa la lectura de metadata.txt. */
    public static DataType parse(String text) {
        String upper = text.trim().toUpperCase();
        if (upper.startsWith("VARCHAR(") && upper.endsWith(")")) {
            return varchar(Integer.parseInt(upper.substring(8, upper.length() - 1)));
        }
        return switch (upper) {
            case "INT" -> INT;
            case "FLOAT" -> FLOAT;
            case "BOOLEAN" -> BOOLEAN;
            case "DATE" -> DATE;
            default -> throw new IllegalArgumentException("tipo desconocido: " + text);
        };
    }
}
