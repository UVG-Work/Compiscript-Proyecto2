package com.uvg.sql.storage;

import com.uvg.sql.types.DataType;
import com.uvg.sql.types.Values;
import java.util.ArrayList;
import java.util.List;

/**
 * Codificacion de valores en los archivos de texto (CSV de tablas e indices).
 *
 * <ul>
 *   <li>NULL es un campo vacio sin comillas.</li>
 *   <li>VARCHAR va siempre entre comillas dobles; una comilla interna se
 *       duplica. Asi la cadena vacia ({@code ""}) no se confunde con NULL.</li>
 *   <li>INT, FLOAT, BOOLEAN (TRUE/FALSE) y DATE (YYYY-MM-DD) van tal cual.</li>
 * </ul>
 *
 * Un registro ocupa una linea: el lexer no acepta saltos de linea dentro de
 * una cadena, asi que ningun valor los contiene.
 */
final class Csv {

    private Csv() {
    }

    static String encode(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return Values.display(value);
    }

    /**
     * Convierte un campo crudo (tal como lo devolvio {@link #split}) al valor
     * de la columna.
     *
     * @throws IllegalArgumentException si el texto no es del tipo esperado
     */
    static Object decode(Field field, DataType type) {
        if (field.quoted()) {
            if (type.kind() != DataType.Kind.VARCHAR) {
                throw new IllegalArgumentException("texto entre comillas en una columna " + type);
            }
            return field.text();
        }
        String text = field.text();
        if (text.isEmpty()) {
            return null;
        }
        return switch (type.kind()) {
            case INT -> Long.parseLong(text);
            case FLOAT -> Double.parseDouble(text);
            case BOOLEAN -> switch (text) {
                case "TRUE" -> Boolean.TRUE;
                case "FALSE" -> Boolean.FALSE;
                default -> throw new IllegalArgumentException("booleano invalido: " + text);
            };
            case DATE -> {
                Object date = Values.parseDate(text);
                if (date == null) {
                    throw new IllegalArgumentException("fecha invalida: " + text);
                }
                yield date;
            }
            default -> throw new IllegalArgumentException("texto sin comillas en una columna " + type);
        };
    }

    /** Un campo de una linea: su texto sin comillas y si venia entre comillas. */
    record Field(String text, boolean quoted) {
    }

    static String join(List<String> encodedFields) {
        return String.join(",", encodedFields);
    }

    /** Parte una linea en campos respetando las comillas. */
    static List<Field> split(String line) {
        List<Field> fields = new ArrayList<>();
        int i = 0;
        while (true) {
            if (i < line.length() && line.charAt(i) == '"') {
                StringBuilder text = new StringBuilder();
                i++;
                while (true) {
                    if (i >= line.length()) {
                        throw new IllegalArgumentException("comilla sin cerrar");
                    }
                    char c = line.charAt(i++);
                    if (c == '"') {
                        if (i < line.length() && line.charAt(i) == '"') {
                            text.append('"');
                            i++;
                        } else {
                            break;
                        }
                    } else {
                        text.append(c);
                    }
                }
                fields.add(new Field(text.toString(), true));
            } else {
                int comma = line.indexOf(',', i);
                int end = comma < 0 ? line.length() : comma;
                fields.add(new Field(line.substring(i, end), false));
                i = end;
            }
            if (i >= line.length()) {
                return fields;
            }
            if (line.charAt(i) != ',') {
                throw new IllegalArgumentException("se esperaba ',' en la columna " + (i + 1));
            }
            i++;
        }
    }
}
