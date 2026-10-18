package com.uvg.compiscript.codegen;

import java.util.TreeSet;

/**
 * Temporales de una funcion. Siempre se entrega el libre de numero mas bajo y
 * un temporal vuelve al pool apenas la instruccion que lo consume se emite, asi
 * que el total creado es el maximo que estuvo vivo a la vez.
 */
final class TempPool {

    private final TreeSet<Integer> free = new TreeSet<>();
    private int created;

    String take() {
        Integer reused = free.pollFirst();
        return "t" + (reused != null ? reused : ++created);
    }

    /** Solo libera temporales; una variable o constante no pertenece al pool. */
    void release(String operand) {
        if (isTemp(operand)) {
            free.add(Integer.parseInt(operand.substring(1)));
        }
    }

    int count() {
        return created;
    }

    static boolean isTemp(String operand) {
        return operand != null && operand.matches("t\\d+");
    }
}
