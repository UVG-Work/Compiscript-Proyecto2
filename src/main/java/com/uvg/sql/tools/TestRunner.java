package com.uvg.sql.tools;

import com.uvg.sql.pipeline.Interpreter;
import com.uvg.sql.pipeline.Report;
import com.uvg.sql.pipeline.RunResult;
import com.uvg.sql.storage.Database;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Bateria de pruebas: cada {@code tests/NN_nombre.sql} se ejecuta sobre una
 * base vacia y nueva, y su salida se compara linea por linea con
 * {@code tests/NN_nombre.out}.
 *
 * <p>Marcas que se pueden poner en el script, cada una en su propia linea:
 * <ul>
 *   <li>{@code -- @reabrir}: termina el tramo actual y abre la base de nuevo
 *       desde los archivos. Prueba que lo guardado se lee igual.</li>
 *   <li>{@code -- @ast}: agrega el AST de cada tramo a la salida.</li>
 * </ul>
 *
 * <p>Con {@code --update} se reescriben los .out con la salida actual; sirve
 * cuando un cambio de comportamiento es intencional, y el diff de los .out
 * muestra exactamente que cambio.
 */
public final class TestRunner {

    private static final String REOPEN = "-- @reabrir";
    private static final String AST = "-- @ast";

    private TestRunner() {
    }

    /** @return 0 si todas pasan, 1 si alguna falla, 2 si el directorio no existe. */
    public static int run(Path directory, boolean update, PrintStream out) throws IOException {
        if (!Files.isDirectory(directory)) {
            out.println("no es un directorio: " + directory);
            return 2;
        }
        List<Path> scripts;
        try (Stream<Path> files = Files.list(directory)) {
            scripts = files.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        }

        int passed = 0;
        int failed = 0;
        for (Path script : scripts) {
            String name = script.getFileName().toString();
            Path golden = Path.of(script.toString().replaceFirst("\\.sql$", ".out"));
            String actual = execute(script);
            if (update) {
                Files.writeString(golden, actual);
                out.printf("  escrito  %s%n", golden.getFileName());
                continue;
            }
            String problem = !Files.exists(golden)
                    ? "falta " + golden.getFileName() + " (genera con --update)"
                    : compare(Files.readString(golden), actual);
            if (problem == null) {
                out.printf("  ok       %s%n", name);
                passed++;
            } else {
                out.printf("  FALLO    %s: %s%n", name, problem);
                failed++;
            }
        }
        if (!update) {
            out.printf("%n%d prueba(s) pasaron, %d fallaron%n", passed, failed);
        }
        return failed == 0 ? 0 : 1;
    }

    /** Corre el script tramo por tramo sobre una base temporal y junta la salida. */
    static String execute(Path script) throws IOException {
        String source = Files.readString(script).replace("\r\n", "\n");
        boolean showAst = source.lines().anyMatch(line -> line.trim().equals(AST));
        Path dir = Files.createTempDirectory("sql-prueba-");
        try {
            StringBuilder out = new StringBuilder();
            // Cada tramo conserva sus lineas originales: los tramos siguientes
            // se rellenan con lineas vacias para que los errores reporten la
            // misma linea que en el archivo.
            String[] lines = source.split("\n", -1);
            int start = 0;
            for (int i = 0; i <= lines.length; i++) {
                if (i == lines.length || lines[i].trim().equals(REOPEN)) {
                    String segment = "\n".repeat(start)
                            + String.join("\n", List.of(lines).subList(start, i));
                    RunResult run = Interpreter.run(segment, Database.open(dir), true);
                    if (showAst && run.ast() != null) {
                        out.append(run.ast().render()).append('\n');
                    }
                    out.append(Report.render(run));
                    if (i < lines.length) {
                        if (!run.errors().isEmpty()) {
                            out.append('\n');
                        }
                        out.append("-- base reabierta desde los archivos\n\n");
                    }
                    start = i + 1;
                }
            }
            return out.toString();
        } finally {
            deleteRecursively(dir);
        }
    }

    private static String compare(String expected, String actual) {
        String[] want = expected.replace("\r\n", "\n").stripTrailing().split("\n", -1);
        String[] got = actual.stripTrailing().split("\n", -1);
        for (int i = 0; i < Math.max(want.length, got.length); i++) {
            String a = i < want.length ? want[i] : "(fin)";
            String b = i < got.length ? got[i] : "(fin)";
            if (!a.equals(b)) {
                return "linea " + (i + 1) + " de la salida: se esperaba [" + a + "] y salio [" + b + "]";
            }
        }
        return null;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
