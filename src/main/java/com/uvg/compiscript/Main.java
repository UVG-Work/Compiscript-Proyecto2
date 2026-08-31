package com.uvg.compiscript;

import com.uvg.compiscript.compiler.CompilationResult;
import com.uvg.compiscript.compiler.CompilerService;
import com.uvg.compiscript.ide.IdeApp;
import com.uvg.compiscript.parser.CompiscriptLexer;
import com.uvg.compiscript.parser.CompiscriptParser;
import com.uvg.compiscript.semantic.SemanticError;
import com.uvg.compiscript.tools.TestRunner;
import com.uvg.compiscript.tools.TreeExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;

/**
 * Driver de linea de comandos.
 *
 *   java -jar compiscript.jar programa.cps [--tree] [--tokens] [--dot] [--quiet]
 *   java -jar compiscript.jar --test tests
 *   java -jar compiscript.jar --ide
 *
 * Codigo de salida: 0 sin errores, 1 con errores, 2 uso invalido. Los tres los
 * distinguen el arnes de pruebas y cualquier CI.
 */
public class Main {

    private static final String USAGE = """
            uso: java -jar compiscript.jar <archivo.cps> [opciones]
                 java -jar compiscript.jar --test <directorio>
                 java -jar compiscript.jar --ide [archivo.cps]

            opciones:
              --tree     imprime el arbol de parseo en texto
              --tokens   imprime la secuencia de tokens del lexer
              --dot      exporta el arbol a .dot y, si hay graphviz, a .png
              --quiet    no imprime la tabla de simbolos""";

    public static void main(String[] args) throws IOException {
        String file = null;
        String testDirectory = null;
        boolean showTree = false;
        boolean showTokens = false;
        boolean exportDot = false;
        boolean quiet = false;
        boolean ide = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--tree" -> showTree = true;
                case "--tokens" -> showTokens = true;
                case "--dot" -> exportDot = true;
                case "--quiet" -> quiet = true;
                case "--ide" -> ide = true;
                case "--test" -> {
                    if (++i >= args.length) {
                        System.err.println(USAGE);
                        System.exit(2);
                    }
                    testDirectory = args[i];
                }
                case "-h", "--help" -> {
                    System.out.println(USAGE);
                    System.exit(0);
                }
                default -> file = args[i];
            }
        }

        if (ide) {
            IdeApp.launch(file);
            return;
        }
        if (testDirectory != null) {
            System.exit(TestRunner.run(Path.of(testDirectory)));
        }
        if (file == null) {
            System.err.println(USAGE);
            System.exit(2);
        }
        if (!Files.isRegularFile(Path.of(file))) {
            System.err.println("no se encontro el archivo: " + file);
            System.exit(2);
        }

        System.out.println("=== " + file + " ===");
        System.exit(analyze(file, showTree, showTokens, exportDot, quiet));
    }

    /** @return 0 si el archivo esta limpio, 1 si tiene errores. */
    public static int analyze(String file, boolean showTree, boolean showTokens,
                              boolean exportDot, boolean quiet) throws IOException {
        CompilationResult result = CompilerService.compileFile(Path.of(file));

        if (showTokens) {
            printTokens(result);
        }

        if (!result.parsedCleanly()) {
            List<SemanticError> syntax = result.syntaxErrors();
            System.out.println("Errores de sintaxis (" + syntax.size() + "):");
            syntax.forEach(error -> System.out.println("  " + error));
            System.out.println("RESULTADO: FALLO");
            return 1;
        }

        if (showTree) {
            System.out.println("Arbol de parseo:");
            System.out.println("  " + Trees.toStringTree(result.tree(), result.parser()));
        }
        if (exportDot) {
            exportTree(result.tree(), (CompiscriptParser) result.parser(), file);
        }

        if (!quiet) {
            System.out.println("Tabla de simbolos:");
            System.out.print(result.globalScope().dump());
        }

        List<SemanticError> warnings = result.warnings();
        if (!warnings.isEmpty()) {
            System.out.println("Advertencias (" + warnings.size() + "):");
            warnings.forEach(warning -> System.out.println("  " + warning));
        }

        List<SemanticError> errors = result.errors();
        if (!errors.isEmpty()) {
            System.out.println("Errores semanticos (" + errors.size() + "):");
            errors.forEach(error -> System.out.println("  " + error));
            System.out.println("RESULTADO: FALLO");
            return 1;
        }

        System.out.println("RESULTADO: OK, sin errores.");
        return 0;
    }

    private static void exportTree(ParseTree tree, CompiscriptParser parser, String file)
            throws IOException {
        String base = file.replaceFirst("\\.cps$", "") + "_arbol";
        Path dot = TreeExporter.writeDot(tree, parser, Path.of(base + ".dot"));
        System.out.println("  dot      : " + dot);
        Path png = TreeExporter.renderPng(dot, Path.of(base + ".png"));
        System.out.println("  imagen   : "
                + (png != null ? png.toString() : "graphviz no disponible, quedo solo el .dot"));
    }

    private static void printTokens(CompilationResult result) {
        System.out.println("Tokens:");
        for (Token token : result.tokens().getTokens()) {
            if (token.getType() == Token.EOF) {
                continue;
            }
            System.out.printf("  %-16s %s%n",
                    CompiscriptLexer.VOCABULARY.getDisplayName(token.getType()),
                    token.getText());
        }
    }
}
