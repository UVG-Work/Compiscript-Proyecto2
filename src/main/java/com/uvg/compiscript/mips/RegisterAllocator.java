package com.uvg.compiscript.mips;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * getReg: los registros son una cache de la memoria dentro de un bloque basico.
 *
 * <p>Lleva dos descriptores, el de registros (que nombre guarda cada uno) y el
 * de direcciones (en que registro esta cada nombre), y un registro "sucio" es
 * uno cuyo valor todavia no se escribio a memoria. Los temporales del TAC van a
 * {@code $t0-$t7} y las variables a {@code $s0-$s7}; {@code $t8} y {@code $t9}
 * quedan libres para constantes y direcciones.
 *
 * <p>Si no hay registro libre se desaloja el que se va a leer mas tarde (o
 * nunca), y si estaba sucio y su valor sigue vivo se derrama a su lugar en
 * memoria: el registro de activacion, en la pila, o el area estatica.
 */
final class RegisterAllocator {

    static final List<String> TEMP_REGS =
            List.of("$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7");
    static final List<String> SAVED_REGS =
            List.of("$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7");

    private final Map<String, String> contents = new HashMap<>();
    private final Map<String, String> location = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();
    private final Set<String> savedUsed = new TreeSet<>();
    private final Asm out;
    private final Function<String, String> home;
    private final Liveness liveness;
    private int current;

    RegisterAllocator(Asm out, Function<String, String> home, Liveness liveness) {
        this.out = out;
        this.home = home;
        this.liveness = liveness;
    }

    /** La instruccion que se esta traduciendo; de ella dependen vida y proximo uso. */
    void at(int index) {
        current = index;
    }

    /**
     * El registro de {@code name}. Si no estaba en uno se le asigna y, si
     * {@code load}, se carga desde memoria. Los nombres en {@code pinned} son
     * operandos de la instruccion actual y no se pueden desalojar.
     */
    String getReg(String name, boolean load, Set<String> pinned) {
        String reg = location.get(name);
        if (reg != null) {
            return reg;
        }
        List<String> pool = Tac.isTemp(name) ? TEMP_REGS : SAVED_REGS;
        reg = pool.stream().filter(r -> !contents.containsKey(r)).findFirst()
                .orElseGet(() -> evict(pool, pinned));
        contents.put(reg, name);
        location.put(name, reg);
        if (pool == SAVED_REGS) {
            savedUsed.add(reg);
        }
        if (load) {
            out.op("lw " + reg + ", " + home.apply(name), name);
        }
        return reg;
    }

    private String evict(List<String> pool, Set<String> pinned) {
        String victim = null;
        long best = -1;
        for (String reg : pool) {
            String name = contents.get(reg);
            if (pinned.contains(name)) {
                continue;
            }
            long distance = needed(name) ? liveness.nextUse(name, current) : Integer.MAX_VALUE;
            // A igual distancia conviene el limpio: desalojarlo no cuesta un store.
            long score = distance * 2 + (dirty.contains(reg) && needed(name) ? 0 : 1);
            if (score > best) {
                best = score;
                victim = reg;
            }
        }
        if (victim == null) {
            throw new IllegalStateException("no hay registro que desalojar");
        }
        String name = contents.get(victim);
        if (dirty.contains(victim) && needed(name)) {
            out.op("sw " + victim + ", " + home.apply(name), "derrame de " + name);
        }
        drop(victim);
        return victim;
    }

    private boolean needed(String name) {
        return Tac.isVariable(name) || liveness.liveAfter(current).contains(name);
    }

    void markDirty(String reg) {
        dirty.add(reg);
    }

    /** Un temporal muerto suelta su registro sin escribirse a memoria. */
    void release(String name) {
        String reg = location.get(name);
        if (reg != null) {
            drop(reg);
        }
    }

    /** Escribe a memoria los registros sucios cuyo nombre cumple {@code keep}; los demas se descartan. */
    void writeBack(Predicate<String> keep) {
        for (String reg : new TreeSet<>(dirty)) {
            String name = contents.get(reg);
            if (keep.test(name)) {
                out.op("sw " + reg + ", " + home.apply(name), "guarda " + name);
            }
        }
        dirty.clear();
    }

    /** Despues de un salto o de una llamada ningun registro conserva un valor confiable. */
    void clear() {
        contents.clear();
        location.clear();
        dirty.clear();
    }

    private void drop(String reg) {
        location.remove(contents.remove(reg));
        dirty.remove(reg);
    }

    /** Los {@code $s} que la funcion toco: los guarda el prologo y los restaura el epilogo. */
    Set<String> savedUsed() {
        return savedUsed;
    }
}
