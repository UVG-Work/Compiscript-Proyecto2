package com.uvg.compiscript.mips;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ejecuta un programa MIPS en el simulador MARS, como proceso aparte.
 *
 * <p>El jar se busca en {@code -Dmars.jar}, en la variable {@code MARS_JAR},
 * en {@code lib/Mars4_5.jar} desde el directorio actual y junto al jar del
 * compilador ({@code target/../lib}).
 */
public final class MarsRunner {

    /** Tope de instrucciones: un ciclo infinito termina en vez de colgar la bateria. */
    private static final int MAX_STEPS = 50_000_000;
    private static final int TIMEOUT_SECONDS = 60;

    /** {@code output} es lo que imprimio el programa; {@code messages}, lo que dijo MARS. */
    public record Run(String output, String messages, int exitCode) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    private MarsRunner() {
    }

    public static Path locate() {
        List<Path> candidates = new ArrayList<>();
        if (System.getProperty("mars.jar") != null) {
            candidates.add(Path.of(System.getProperty("mars.jar")));
        }
        if (System.getenv("MARS_JAR") != null) {
            candidates.add(Path.of(System.getenv("MARS_JAR")));
        }
        candidates.add(Path.of("lib", "Mars4_5.jar"));
        try {
            Path self = Path.of(MarsRunner.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            candidates.add(self.getParent().resolveSibling("lib").resolve("Mars4_5.jar"));
        } catch (URISyntaxException | RuntimeException ignored) {
            // Sin ubicacion conocida quedan los demas candidatos.
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    public static Run run(String assembly) throws IOException {
        Path mars = locate();
        if (mars == null) {
            throw new IOException("no se encontro MARS: se busca en lib/Mars4_5.jar, "
                    + "-Dmars.jar o la variable MARS_JAR");
        }
        Path source = Files.createTempFile("compiscript", ".asm");
        Path stdout = Files.createTempFile("compiscript", ".out");
        Path stderr = Files.createTempFile("compiscript", ".err");
        try {
            Files.writeString(source, assembly);
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            // nc: sin aviso de copyright; me: mensajes de MARS a stderr;
            // ae2 / se3: codigos de salida para errores de ensamblado y de ejecucion.
            Process process = new ProcessBuilder(java, "-Djava.awt.headless=true", "-jar",
                    mars.toString(), "nc", "me", "ae2", "se3", String.valueOf(MAX_STEPS),
                    source.toString())
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Run(read(stdout), "MARS no termino en " + TIMEOUT_SECONDS + " s", -1);
            }
            return new Run(read(stdout), read(stderr), process.exitValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ejecucion interrumpida", e);
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
        }
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
