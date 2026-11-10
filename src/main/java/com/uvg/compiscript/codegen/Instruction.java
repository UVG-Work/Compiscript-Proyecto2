package com.uvg.compiscript.codegen;

import com.uvg.compiscript.types.Type;

/**
 * Una linea de TAC. El comentario es el fuente que la origino, si aplica.
 *
 * <p>{@code type} no se imprime: es el tipo de los operandos en las
 * instrucciones donde el TAC no lo dice y la maquina si lo necesita (suma
 * entera o de punto flotante, que imprime un {@code print}, que lista revisa
 * un acceso indexado). En las demas es null.
 */
public record Instruction(String text, String comment, Type type) {

    public Instruction(String text, String comment) {
        this(text, comment, null);
    }

    public boolean isLabel() {
        return text.endsWith(":");
    }
}
