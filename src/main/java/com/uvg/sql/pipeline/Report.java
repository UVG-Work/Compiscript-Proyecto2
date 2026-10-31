package com.uvg.sql.pipeline;

import com.uvg.sql.diagnostics.Diagnostic;
import com.uvg.sql.engine.StatementResult;
import com.uvg.sql.pipeline.RunResult.Executed;
import com.uvg.sql.types.Values;
import java.util.List;

/**
 * Convierte una corrida en texto: resultados como tablas, el plan de acceso y
 * los errores. Es la salida del CLI y lo que la bateria de pruebas compara
 * contra los archivos .out, asi que debe ser determinista.
 */
public final class Report {

    private Report() {
    }

    public static String render(RunResult run) {
        StringBuilder out = new StringBuilder();
        for (Executed executed : run.executed()) {
            StatementResult result = executed.result();
            out.append("> ").append(firstLine(executed)).append('\n');
            result.plan().forEach(line -> out.append("  plan: ").append(line).append('\n'));
            if (result.isQuery()) {
                out.append(table(result.columns(), result.rows()));
            }
            out.append(result.summary()).append("\n\n");
        }
        for (Diagnostic error : run.errors()) {
            out.append(error).append('\n');
        }
        return out.toString();
    }

    /** Encabezado de cada resultado: el tipo de sentencia y su posicion. */
    private static String firstLine(Executed executed) {
        String kind = executed.statement().getClass().getSimpleName();
        return kind + " (linea " + executed.statement().pos().line() + ")";
    }

    /** Tabla con bordes y columnas al ancho de su valor mas largo. */
    public static String table(List<String> columns, List<Object[]> rows) {
        int[] widths = new int[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            widths[c] = columns.get(c).length();
            for (Object[] row : rows) {
                widths[c] = Math.max(widths[c], Values.display(row[c]).length());
            }
        }
        StringBuilder out = new StringBuilder();
        String rule = rule(widths);
        out.append(rule).append(line(columns.toArray(), widths, true)).append(rule);
        for (Object[] row : rows) {
            out.append(line(row, widths, false));
        }
        if (!rows.isEmpty()) {
            out.append(rule);
        }
        return out.toString();
    }

    private static String rule(int[] widths) {
        StringBuilder out = new StringBuilder("+");
        for (int width : widths) {
            out.append("-".repeat(width + 2)).append('+');
        }
        return out.append('\n').toString();
    }

    /** Los numeros se alinean a la derecha, el resto a la izquierda. */
    private static String line(Object[] values, int[] widths, boolean header) {
        StringBuilder out = new StringBuilder("|");
        for (int c = 0; c < values.length; c++) {
            String text = header ? (String) values[c] : Values.display(values[c]);
            String format = !header && values[c] instanceof Number ? "%" + widths[c] + "s" : "%-" + widths[c] + "s";
            out.append(' ').append(String.format(format, text)).append(" |");
        }
        return out.append('\n').toString();
    }
}
