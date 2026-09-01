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
 * <p>Al final imprime la matriz de cobertura: el enunciado pide casos exitosos y
 * fallidos <em>para cada</em> regla semantica, asi que una regla sin cubrir es un
 * fallo de la bateria, no un detalle.
 */
public final class TestRunner {

    private record Case(Path file, String expectation, Set<String> rules) {
    }

    private record Outcome(int syntaxErrors, int semanticErrors, List<String> messages) {
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
        try (Stream<Path> found = Files.list(directory)) {
            files = found.filter(p -> p.toString().endsWith(".cps")).sorted().toList();
        }

        Map<String, Set<String>> okBy = new TreeMap<>();
        Map<String, Set<String>> errBy = new TreeMap<>();
        int passed = 0;
        int failed = 0;

        for (Path file : files) {
            Case testCase = read(file);
            if (testCase == null) {
                out.printf("  OMITIDA  %-28s sin cabecera @esperado%n", file.getFileName());
                continue;
            }

            Outcome outcome;
            String problem;
            try {
                outcome = analyze(file);
                problem = compare(testCase.expectation(), outcome);
            } catch (RuntimeException crash) {
                // Un caso que tumba al analizador no debe abortar la bateria:
                // se reporta como fallo y se sigue con los demas.
                outcome = new Outcome(0, 0, List.of(crash.toString()));
                problem = "el analizador reviento: " + crash;
            }
            if (problem == null) {
                out.printf("  ok       %-28s %s%n", file.getFileName(), testCase.expectation());
                passed++;
            } else {
                out.printf("  FALLO    %-28s %s%n", file.getFileName(), problem);
                outcome.messages().forEach(message -> out.println("             " + message));
                failed++;
            }

            Map<String, Set<String>> target =
                    testCase.expectation().equals("OK") ? okBy : errBy;
            for (String rule : testCase.rules()) {
                target.computeIfAbsent(rule, key -> new LinkedHashSet<>())
                        .add(file.getFileName().toString());
            }
        }

        out.printf("%n%d prueba(s) pasaron, %d fallaron%n", passed, failed);
        int uncovered = reportCoverage(out, okBy, errBy);
        return failed == 0 && uncovered == 0 ? 0 : 1;
    }

    /** @return cuantas reglas quedaron sin cubrir. */
    private static int reportCoverage(PrintStream out, Map<String, Set<String>> okBy,
                                      Map<String, Set<String>> errBy) {
        out.println();
        out.println("Cobertura de las " + RuleCatalog.RULES.size()
                + " reglas semanticas del enunciado:");

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

    private static String compare(String expected, Outcome outcome) {
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
        int wanted = Integer.parseInt(expected.substring("ERRORES".length()).trim());
        return outcome.semanticErrors() == wanted ? null
                : "se esperaban " + wanted + " error(es) y hubo " + outcome.semanticErrors();
    }

    private static Case read(Path file) throws IOException {
        String expectation = null;
        Set<String> rules = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file)) {
            int expected = line.indexOf("@esperado:");
            if (expected >= 0) {
                expectation = line.substring(expected + "@esperado:".length()).trim();
            }
            int tagged = line.indexOf("@regla:");
            if (tagged >= 0) {
                for (String rule : line.substring(tagged + "@regla:".length()).split(",")) {
                    if (!rule.isBlank()) {
                        rules.add(rule.trim());
                    }
                }
            }
        }
        return expectation == null ? null : new Case(file, expectation, rules);
    }

    private static Outcome analyze(Path file) throws IOException {
        CompilationResult result = CompilerService.compileFile(file);
        List<String> messages = new ArrayList<>();
        for (SemanticError error : result.errors()) {
            messages.add(error.toString());
        }
        if (!result.parsedCleanly()) {
            return new Outcome(result.syntaxErrors().size(), 0, messages);
        }
        return new Outcome(0, result.errors().size(), messages);
    }
}
