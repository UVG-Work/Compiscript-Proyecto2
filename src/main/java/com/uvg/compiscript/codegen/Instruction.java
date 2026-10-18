package com.uvg.compiscript.codegen;

/** Una linea de TAC. El comentario es el fuente que la origino, si aplica. */
public record Instruction(String text, String comment) {

    public boolean isLabel() {
        return text.endsWith(":");
    }
}
