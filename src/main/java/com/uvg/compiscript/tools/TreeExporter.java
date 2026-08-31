package com.uvg.compiscript.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Vuelca el arbol de parseo a DOT y, si {@code dot} esta instalado, a PNG.
 *
 * <p>{@code grun -gui} abre una ventana Swing y no sirve dentro del contenedor,
 * que no tiene entorno grafico.
 */
public final class TreeExporter {

    private TreeExporter() {
    }

    public static Path writeDot(ParseTree tree, Parser parser, Path target) throws IOException {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph arbol {\n")
                .append("  graph [rankdir=TB, fontname=\"Helvetica\"];\n")
                .append("  node  [fontname=\"Helvetica\", fontsize=10];\n");
        emit(tree, parser, dot, new int[]{0});
        dot.append("}\n");
        Files.writeString(target, dot.toString());
        return target;
    }

    /** @return la ruta del PNG, o null si {@code dot} no esta disponible. */
    public static Path renderPng(Path dotFile, Path target) {
        try {
            Process process = new ProcessBuilder("dot", "-Tpng",
                    dotFile.toString(), "-o", target.toString())
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0 ? target : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static int emit(ParseTree node, Parser parser, StringBuilder dot, int[] counter) {
        int id = counter[0]++;
        if (node instanceof TerminalNode) {
            dot.append(String.format("  n%d [label=\"%s\", shape=box, style=filled,"
                    + " fillcolor=\"#fde68a\"];%n", id, escape(node.getText())));
        } else {
            String rule = parser.getRuleNames()[((RuleNode) node).getRuleContext().getRuleIndex()];
            dot.append(String.format("  n%d [label=\"%s\", shape=ellipse, style=filled,"
                    + " fillcolor=\"#bfdbfe\"];%n", id, escape(rule)));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            int child = emit(node.getChild(i), parser, dot, counter);
            dot.append(String.format("  n%d -> n%d;%n", id, child));
        }
        return id;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
