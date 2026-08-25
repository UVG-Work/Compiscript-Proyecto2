package com.uvg.compiscript;

import com.uvg.compiscript.parser.CompiscriptLexer;
import com.uvg.compiscript.parser.CompiscriptParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;

/**
 * Driver de linea de comandos.
 *
 *   java -jar compiscript.jar programa.cps [--tree] [--tokens]
 *
 * Codigo de salida: 0 si no hubo errores, 1 si hubo alguno.
 */
public class Main {

    private static final String USAGE =
            "uso: java -jar compiscript.jar <archivo.cps> [--tree] [--tokens]";

    public static void main(String[] args) throws IOException {
        String file = null;
        boolean showTree = false;
        boolean showTokens = false;

        for (String arg : args) {
            switch (arg) {
                case "--tree" -> showTree = true;
                case "--tokens" -> showTokens = true;
                case "-h", "--help" -> {
                    System.out.println(USAGE);
                    System.exit(0);
                }
                default -> file = arg;
            }
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
        System.exit(analyze(file, showTree, showTokens));
    }

    private static int analyze(String file, boolean showTree, boolean showTokens)
            throws IOException {
        CompiscriptLexer lexer = new CompiscriptLexer(CharStreams.fromFileName(file));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CompiscriptParser parser = new CompiscriptParser(tokens);

        SyntaxErrorListener listener = new SyntaxErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        if (showTokens) {
            tokens.fill();
            printTokens(tokens);
        }

        ParseTree tree = parser.program();

        if (listener.hasErrors()) {
            System.out.println("Errores de sintaxis (" + listener.getErrors().size() + "):");
            for (SyntaxErrorListener.SyntaxError error : listener.getErrors()) {
                System.out.println("  " + error);
            }
            System.out.println("RESULTADO: FALLO");
            return 1;
        }

        if (showTree) {
            System.out.println("Arbol de parseo:");
            System.out.println("  " + Trees.toStringTree(tree, parser));
        }

        // TODO: recorrer el arbol con el analizador semantico.
        System.out.println("analisis semantico: pendiente");
        System.out.println("RESULTADO: OK, sin errores de sintaxis.");
        return 0;
    }

    private static void printTokens(CommonTokenStream tokens) {
        System.out.println("Tokens:");
        for (Token token : tokens.getTokens()) {
            if (token.getType() == Token.EOF) {
                continue;
            }
            System.out.printf("  %-16s %s%n",
                    CompiscriptLexer.VOCABULARY.getDisplayName(token.getType()),
                    token.getText());
        }
    }
}
