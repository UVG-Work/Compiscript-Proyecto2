package com.uvg.sql;

import com.uvg.sql.ide.IdeApp;
import com.uvg.sql.parser.SqlLexer;
import com.uvg.sql.pipeline.Interpreter;
import com.uvg.sql.pipeline.Report;
import com.uvg.sql.pipeline.RunResult;
import com.uvg.sql.storage.Database;
import com.uvg.sql.storage.StorageException;
import com.uvg.sql.tools.TestRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.Trees;

/**
 * Linea de comandos.
 *
 * <pre>
 *   java -jar sql-interprete.jar script.sql [--db dir] [--check] [--tokens] [--tree] [--ast]
 *   java -jar sql-interprete.jar --test tests [--update]
 *   java -jar sql-interprete.jar --ide [--db dir] [script.sql]
 * </pre>
 *
 * Codigo de salida: 0 sin errores, 1 con errores, 2 uso invalido.
 */
public final class Main {

    private static final String USAGE = """
            uso: java -jar sql-interprete.jar <script.sql> [opciones]
                 java -jar sql-interprete.jar --test <directorio> [--update]
                 java -jar sql-interprete.jar --ide [--db <dir>] [script.sql]

            opciones:
              --db <dir>  directorio de la base de datos (por defecto: database)
              --check     solo analiza (lexico, sintactico, semantico); no ejecuta
              --tokens    imprime los tokens del lexer
              --tree      imprime el arbol sintactico (arbol de parseo de ANTLR)
              --ast       imprime el arbol de sintaxis abstracta
              --test      corre la bateria: cada .sql contra su .out
              --update    con --test, reescribe los .out con la salida actual""";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String file = null;
        Path dbDir = Path.of("database");
        boolean execute = true;
        boolean showTokens = false;
        boolean showTree = false;
        boolean showAst = false;
        String testDir = null;
        boolean update = false;
        boolean ide = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--db" -> {
                    if (++i >= args.length) {
                        usage();
                    }
                    dbDir = Path.of(args[i]);
                }
                case "--check" -> execute = false;
                case "--tokens" -> showTokens = true;
                case "--tree" -> showTree = true;
                case "--ast" -> showAst = true;
                case "--update" -> update = true;
                case "--ide" -> ide = true;
                case "--test" -> {
                    if (++i >= args.length) {
                        usage();
                    }
                    testDir = args[i];
                }
                case "-h", "--help" -> {
                    System.out.println(USAGE);
                    System.exit(0);
                }
                default -> file = args[i];
            }
        }
        if (ide) {
            IdeApp.launch(dbDir, file == null ? null : Path.of(file));
            return;
        }
        if (testDir != null) {
            System.exit(TestRunner.run(Path.of(testDir), update, System.out));
        }
        if (file == null) {
            usage();
        }
        if (!Files.isRegularFile(Path.of(file))) {
            System.err.println("no se encontro el archivo: " + file);
            System.exit(2);
        }

        Database db;
        try {
            db = Database.open(dbDir);
        } catch (StorageException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        RunResult run = Interpreter.run(Files.readString(Path.of(file)), db, execute);

        if (showTokens) {
            printTokens(run);
        }
        if (showTree) {
            System.out.println("Arbol sintactico:");
            System.out.println("  " + Trees.toStringTree(run.parse().tree(), run.parse().parser()));
        }
        if (showAst && run.ast() != null) {
            System.out.println("AST:");
            System.out.print(run.ast().render());
        }
        System.out.print(Report.render(run));
        System.exit(run.ok() ? 0 : 1);
    }

    private static void usage() {
        System.err.println(USAGE);
        System.exit(2);
    }

    private static void printTokens(RunResult run) {
        System.out.println("Tokens:");
        for (Token token : run.parse().tokens().getTokens()) {
            if (token.getType() != Token.EOF) {
                System.out.printf("  %3d:%-3d %-12s %s%n", token.getLine(),
                        token.getCharPositionInLine(),
                        SqlLexer.VOCABULARY.getSymbolicName(token.getType()), token.getText());
            }
        }
    }
}
