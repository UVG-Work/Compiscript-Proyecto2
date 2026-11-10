package com.uvg.compiscript.mips;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bloques basicos y vida de los temporales de una funcion.
 *
 * <p>Un bloque empieza en una etiqueta o despues de un salto y termina en un
 * salto o un return. La vida es el analisis clasico hacia atras,
 * {@code in = use U (out - def)}, iterado hasta que no cambia. Solo se calcula
 * para temporales: las variables viven en memoria y siempre se consideran vivas.
 *
 * <p>Si la funcion tiene un try, cualquier bloque puede saltar a su manejador
 * por un error, asi que el manejador es sucesor de todos.
 */
final class Liveness {

    private final List<Tac> code;
    private final int[] blockStart;
    private final int[] blockEnd;
    private final int[] blockOf;
    private final List<Set<String>> liveAfter = new ArrayList<>();

    Liveness(List<Tac> code) {
        this.code = code;
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            if (i == 0 || code.get(i).kind() == Tac.Kind.LABEL || code.get(i - 1).endsBlock()) {
                starts.add(i);
            }
        }
        int blocks = starts.size();
        blockStart = new int[blocks];
        blockEnd = new int[blocks];
        blockOf = new int[code.size()];
        Map<String, Integer> labelBlock = new HashMap<>();
        for (int b = 0; b < blocks; b++) {
            blockStart[b] = starts.get(b);
            blockEnd[b] = b + 1 < blocks ? starts.get(b + 1) - 1 : code.size() - 1;
            for (int i = blockStart[b]; i <= blockEnd[b]; i++) {
                blockOf[i] = b;
                if (code.get(i).kind() == Tac.Kind.LABEL) {
                    labelBlock.put(code.get(i).label(), b);
                }
            }
        }

        List<Set<Integer>> successors = new ArrayList<>();
        Set<Integer> handlers = new HashSet<>();
        for (Tac tac : code) {
            if (tac.kind() == Tac.Kind.TRY) {
                handlers.add(labelBlock.get(tac.label()));
            }
        }
        for (int b = 0; b < blocks; b++) {
            Set<Integer> next = new HashSet<>(handlers);
            Tac last = code.get(blockEnd[b]);
            switch (last.kind()) {
                case GOTO -> next.add(labelBlock.get(last.label()));
                case IF, IF_FALSE -> {
                    next.add(labelBlock.get(last.label()));
                    if (b + 1 < blocks) {
                        next.add(b + 1);
                    }
                }
                case RETURN -> {
                }
                default -> {
                    if (b + 1 < blocks) {
                        next.add(b + 1);
                    }
                }
            }
            successors.add(next);
        }

        List<Set<String>> in = new ArrayList<>();
        List<Set<String>> out = new ArrayList<>();
        for (int b = 0; b < blocks; b++) {
            in.add(new HashSet<>());
            out.add(new HashSet<>());
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int b = blocks - 1; b >= 0; b--) {
                Set<String> newOut = new HashSet<>();
                successors.get(b).forEach(s -> newOut.addAll(in.get(s)));
                Set<String> newIn = transfer(b, newOut);
                if (!newOut.equals(out.get(b)) || !newIn.equals(in.get(b))) {
                    out.set(b, newOut);
                    in.set(b, newIn);
                    changed = true;
                }
            }
        }

        for (int i = 0; i < code.size(); i++) {
            liveAfter.add(null);
        }
        for (int b = 0; b < blocks; b++) {
            Set<String> live = new HashSet<>(out.get(b));
            for (int i = blockEnd[b]; i >= blockStart[b]; i--) {
                liveAfter.set(i, new HashSet<>(live));
                step(code.get(i), live);
            }
        }
    }

    private Set<String> transfer(int block, Set<String> out) {
        Set<String> live = new HashSet<>(out);
        for (int i = blockEnd[block]; i >= blockStart[block]; i--) {
            step(code.get(i), live);
        }
        return live;
    }

    private static void step(Tac tac, Set<String> live) {
        if (Tac.isTemp(tac.def())) {
            live.remove(tac.def());
        }
        tac.uses().stream().filter(Tac::isTemp).forEach(live::add);
    }

    /** Los temporales cuyo valor todavia se va a leer despues de la instruccion {@code i}. */
    Set<String> liveAfter(int i) {
        return liveAfter.get(i);
    }

    /**
     * Distancia hasta la proxima lectura de {@code name} dentro del bloque, o
     * {@link Integer#MAX_VALUE} si antes se sobrescribe o el bloque termina.
     */
    int nextUse(String name, int from) {
        int end = blockEnd[blockOf[from]];
        for (int i = from + 1; i <= end; i++) {
            Tac tac = code.get(i);
            if (tac.uses().contains(name)) {
                return i - from;
            }
            if (name.equals(tac.def())) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }
}
