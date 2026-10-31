package com.uvg.sql.types;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Operaciones sobre los valores en tiempo de ejecucion.
 *
 * <p>Representacion: INT es {@link Long}, FLOAT es {@link Double}, VARCHAR es
 * {@link String}, BOOLEAN es {@link Boolean}, DATE es {@link LocalDate} y NULL
 * es {@code null}.
 */
public final class Values {

    /** 'uuuu' y STRICT: rechaza 2024-02-30 en vez de corregirlo a 2024-02-29. */
    private static final DateTimeFormatter ISO_STRICT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private Values() {
    }

    /** @return la fecha, o null si el texto no es una fecha YYYY-MM-DD valida. */
    public static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, ISO_STRICT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Orden total entre dos valores no nulos del mismo tipo (INT y FLOAT se
     * comparan entre si). Lo usan las comparaciones, ORDER BY, MIN/MAX y los
     * indices.
     */
    public static int compare(Object a, Object b) {
        if (a instanceof Long x && b instanceof Long y) {
            return Long.compare(x, y);
        }
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue());
        }
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y);
        }
        if (a instanceof Boolean x && b instanceof Boolean y) {
            return Boolean.compare(x, y);
        }
        if (a instanceof LocalDate x && b instanceof LocalDate y) {
            return x.compareTo(y);
        }
        // fecha = 'YYYY-MM-DD': el analisis semantico ya valido que el texto
        // es una fecha, asi que aqui solo se convierte.
        if (a instanceof LocalDate x && b instanceof String y && parseDate(y) != null) {
            return x.compareTo(parseDate(y));
        }
        if (a instanceof String x && b instanceof LocalDate y && parseDate(x) != null) {
            return parseDate(x).compareTo(y);
        }
        throw new IllegalArgumentException("no se pueden comparar " + a + " y " + b);
    }

    /** Igualdad de SQL entre no nulos: 3 = 3.0 es verdadero. */
    public static boolean equal(Object a, Object b) {
        return compare(a, b) == 0;
    }

    /** Texto de un valor como se muestra en los resultados. */
    public static String display(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        return value.toString();
    }

    /**
     * Ajusta un valor al tipo de la columna donde se va a guardar: un INT en
     * columna FLOAT se vuelve Double y un texto en columna DATE se vuelve
     * fecha. El analisis semantico ya garantizo que la conversion es valida.
     */
    public static Object coerce(Object value, DataType target) {
        if (value == null) {
            return null;
        }
        return switch (target.kind()) {
            case FLOAT -> value instanceof Number n ? n.doubleValue() : value;
            case DATE -> value instanceof String s ? parseDate(s) : value;
            default -> value;
        };
    }
}
