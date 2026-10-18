package com.uvg.compiscript.tools;

import com.uvg.compiscript.compiler.CompilationResult;
import com.uvg.compiscript.compiler.CompilerService;
import com.uvg.compiscript.semantic.SemanticError;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Arnes de la bateria de pruebas.
 *
 * <p>Cada .cps declara en su cabecera que espera y que reglas ejercita:
 * <pre>
 * // @esperado: ERRORES 8
 * // @regla: T1, T2, T4
 * </pre>
 *
 * <p>Se compara el numero <em>exacto</em> de errores, no solo si hubo alguno:
 * asi la bateria se rompe en los dos sentidos, cuando una regla deja de
 * detectarse y cuando aparece un falso positivo nuevo.
 *
 * <p>{@code @esperado: TAC} compara el codigo intermedio generado contra el
 * {@code .tac} del mismo nombre, y {@code @genera:} declara que construcciones
 * traduce. Un caso con errores no debe producir codigo intermedio.
 *
 * <p>Al final imprime las matrices de cobertura: una regla o una construccion
 * sin cubrir es un fallo de la bateria, no un detalle.
 */
public final class TestRunner {

    private record Case(Path file, String expectation, Set<String> rules, Set<String> generated) {
    }

    private record Outcome(int syntaxErrors, int semanticErrors, List<String> messages,
                           String tac) {
    }

    private TestRunner() {
    }

    public static int run(Path directory) throws IOException {
        return run(directory, System.out);
    }

    /** @param out a donde escribir; el IDE lo redirige a su panel de salida. */
    public static int run(Path directory, PrintStream out) throws IOException {
        if (!Files.isDirectory(directory)) {
            out.println("no es un directorio: " + directory);
            return 2;
        }

        List<Path> files;
        try (Stream<Path> found = Files.walk(directory)) {
            files = found.filter(p -> p.toString().endsWith(".cps")).sorted().toList();
        }

        Map<String, Set<String>> okBy = new TreeMap<>();
        Map<String, Set<String>> errBy = new TreeMap<>();
        Map<String, Set<String>> generatedBy = new TreeMap<>();
        int passed = 0;
        int failed = 0;
        int withoutTac = 0;

        for (Path file : files) {
            String name = directory.relativize(file).toString().replace('\\', '/');
            Case testCase = read(file);
            if (testCase == null) {
                out.printf("  OMITIDA  %-32s sin cabecera @esperado%n", name);
                continue;
            }

            Outcome outcome;
            String problem;
            try {
                outcome = analyze(file);
                problem = compare(testCase, outcome);
            } catch (RuntimeException crash) {
                // Un caso que tumba al compilador no debe abortar la bateria:
                // se reporta como fallo y se sigue con los demas.
                outcome = new Outcome(0, 0, List.of(crash.toString()), null);
                problem = "el compilador reviento: " + crash;
            }
            if (problem == null) {
                out.printf("  ok       %-32s %s%n", name, testCase.expectation());
                passed++;
                if (testCase.expectation().startsWith("ERRORES")) {
                    withoutTac++;
                }
            } else {
                out.printf("  FALLO    %-32s %s%n", name, problem);
                outcome.messages().forEach(message -> out.println("             " + message));
                failed++;
            }

            boolean success = testCase.expectation().equals("OK")
                    || testCase.expectation().equals("TAC");
            Map<String, Set<String>> target = success ? okBy : errBy;
            for (String rule : testCase.rules()) {
                target.computeIfAbsent(rule, key -> new LinkedHashSet<>()).add(name);
            }
            if (testCase.expectation().equals("TAC")) {
                for (String construct : testCase.generated()) {
                    generatedBy.computeIfAbsent(construct, key -> new LinkedHashSet<>()).add(name);
                }
            }
        }

        out.printf("%n%d prueba(s) pasaron, %d fallaron%n", passed, failed);
        int uncovered = reportCoverage(out, okBy, errBy)
                + reportGeneration(out, generatedBy, withoutTac);
        return failed == 0 && uncovered == 0 ? 0 : 1;
    }

    /** @return cuantas reglas quedaron sin cubrir. */
    private static int reportCoverage(PrintStream out, Map<String, Set<String>> okBy,
                                      Map<String, Set<String>> errBy) {
        out.println();
        out.println("Cobertura de las " + RuleCatalog.RULES.size() + " reglas semanticas:");

        List<String> gaps = new ArrayList<>();
        for (Map.Entry<String, String> rule : RuleCatalog.RULES.entrySet()) {
            String id = rule.getKey();
            boolean hasOk = okBy.containsKey(id);
            boolean needsErr = RuleCatalog.needsFailingCase(id);
            boolean hasErr = errBy.containsKey(id);

            String okMark = hasOk ? "ok " : "-- ";
            String errMark = !needsErr ? "n/a" : (hasErr ? "err" : "-- ");
            out.printf("  %-3s [%s|%s]  %s%s%n", id, okMark, errMark, rule.getValue(),
                    needsErr ? "" : "   (regla de capacidad)");

            if (!hasOk) {
                gaps.add(id + " sin caso exitoso");
            }
            if (needsErr && !hasErr) {
                gaps.add(id + " sin caso fallido");
            }
        }

        if (gaps.isEmpty()) {
            out.println("  todas las reglas tienen los casos que el enunciado pide");
        } else {
            out.println();
            gaps.forEach(gap -> out.println("  HUECO: " + gap));
        }
        return gaps.size();
    }

    /**
     * Cada construccion necesita un caso cuyo TAC se compare. Los casos
     * fallidos de la generacion son todos los programas con errores: se
     * verifico en cada uno que no se produjera codigo intermedio.
     */
    private static int reportGeneration(PrintStream out, Map<String, Set<String>> generatedBy,
                                        int withoutTac) {
        out.println();
        out.println("Cobertura de la generacion de codigo intermedio ("
                + RuleCatalog.GENERATION.size() + " construcciones):");
        List<String> gaps = new ArrayList<>();
        for (Map.Entry<String, String> construct : RuleCatalog.GENERATION.entrySet()) {
            boolean covered = generatedBy.containsKey(construct.getKey());
            out.printf("  %-3s [%s]  %s%n", construct.getKey(), covered ? "tac" : "-- ",
                    construct.getValue());
            if (!covered) {
                gaps.add(construct.getKey() + " sin caso que compare su TAC");
            }
        }
        out.println("  casos fallidos: " + withoutTac
                + " programa(s) con errores, ninguno genero codigo intermedio");
        if (withoutTac == 0) {
            gaps.add("ningun caso fallido para la generacion");
        }
        if (gaps.isEmpty()) {
            out.println("  todas las construcciones tienen su caso");
        } else {
            out.println();
            gaps.forEach(gap -> out.println("  HUECO: " + gap));
        }
        return gaps.size();
    }

    private static String compare(Case testCase, Outcome outcome) throws IOException {
        String expected = testCase.expectation();
        if (outcome.tac() != null && outcome.semanticErrors() + outcome.syntaxErrors() > 0) {
            return "se genero codigo intermedio para un programa con errores";
        }
        if (expected.equals("SINTAXIS")) {
            return outcome.syntaxErrors() > 0 ? null
                    : "se esperaban errores de sintaxis y no hubo ninguno";
        }
        if (outcome.syntaxErrors() > 0) {
            return "error de sintaxis inesperado (" + outcome.syntaxErrors() + ")";
        }
        if (expected.equals("OK")) {
            return outcome.semanticErrors() == 0 ? null
                    : "se esperaba OK y hubo " + outcome.semanticErrors() + " error(es)";
        }
        if (expected.equals("TAC")) {
            return compareTac(testCase.file(), outcome);
        }
        int wanted = Integer.parseInt(expected.substring("ERRORES".length()).trim());
        return outcome.semanticErrors() == wanted ? null
                : "se esperaban " + wanted + " error(es) y hubo " + outcome.semanticErrors();
    }

    private static String compareTac(Path file, Outcome outcome) throws IOException {
        if (outcome.semanticErrors() > 0) {
            return "se esperaba TAC y hubo " + outcome.semanticErrors() + " error(es)";
        }
        Path golden = Path.of(file.toString().replaceFirst("\\.cps$", ".tac"));
        if (!Files.isRegularFile(golden)) {
            return "falta el TAC esperado " + golden.getFileName();
        }
        String[] want = lines(Files.readString(golden));
        String[] got = lines(outcome.tac());
        for (int i = 0; i < Math.max(want.length, got.length); i++) {
            String a = i < want.length ? want[i] : "(fin)";
            String b = i < got.length ? got[i] : "(fin)";
            if (!a.equals(b)) {
                return "el TAC difiere en la linea " + (i + 1) + ": se esperaba [" + a
                        + "] y se genero [" + b + "]";
            }
        }
        return null;
    }

    private static String[] lines(String text) {
        return text.replace("\r\n", "\n").stripTrailing().split("\n");
    }

    private static Case read(Path file) throws IOException {
        String expectation = null;
        Set<String> rules = new LinkedHashSet<>();
        Set<String> generated = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file)) {
            int expected = line.indexOf("@esperado:");
            if (expected >= 0) {
                expectation = line.substring(expected + "@esperado:".length()).trim();
            }
            collectTags(line, "@regla:", rules);
            collectTags(line, "@genera:", generated);
        }
        return expectation == null ? null : new Case(file, expectation, rules, generated);
    }

    private static void collectTags(String line, String tag, Set<String> into) {
        int at = line.indexOf(tag);
        if (at < 0) {
            return;
        }
        for (String item : line.substring(at + tag.length()).split(",")) {
            if (!item.isBlank()) {
                into.add(item.trim());
            }
        }
    }

    private static Outcome analyze(Path file) throws IOException {
        CompilationResult result = CompilerService.compileFile(file);
        List<String> messages = new ArrayList<>();
        for (SemanticError error : result.errors()) {
            messages.add(error.toString());
        }
        String tac = result.tac() == null ? null : result.tac().render();
        if (!result.parsedCleanly()) {
            return new Outcome(result.syntaxErrors().size(), 0, messages, tac);
        }
        return new Outcome(0, result.errors().size(), messages, tac);
    }
}
