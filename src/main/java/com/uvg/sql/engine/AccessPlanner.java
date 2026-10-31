package com.uvg.sql.engine;

import com.uvg.sql.ast.Expr;
import com.uvg.sql.ast.Expr.Binary;
import com.uvg.sql.ast.Expr.BinaryOp;
import com.uvg.sql.ast.Expr.ColumnRef;
import com.uvg.sql.ast.Expr.InList;
import com.uvg.sql.ast.Expr.Literal;
import com.uvg.sql.ast.SqlText;
import com.uvg.sql.semantic.Analysis;
import com.uvg.sql.semantic.Analysis.ColumnBinding;
import com.uvg.sql.storage.Database;
import com.uvg.sql.storage.Index;
import com.uvg.sql.storage.Index.Lookup;
import com.uvg.sql.storage.Table;
import com.uvg.sql.types.DataType;
import com.uvg.sql.types.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decide como leer una tabla: con un indice o recorriendola completa.
 *
 * <p>Se usa un indice cuando una de las condiciones (los terminos unidos por
 * AND en WHERE y en los ON) tiene la forma {@code columna <op> constante} o
 * {@code columna IN (constantes)}, con {@code <op>} uno de = < <= > >=, y la
 * columna tiene indice. "Constante" es todo lo que ya tiene valor al momento
 * de leer esta tabla: un literal, una columna de la consulta externa o una
 * columna de una tabla anterior del JOIN.
 *
 * <p>El indice solo reduce los registros que se leen; el WHERE completo se
 * sigue evaluando sobre ellos, asi que el resultado es el mismo que sin indice.
 * Si hay varias condiciones indexables se prefiere una igualdad a un rango.
 */
final class AccessPlanner {

    /** Los registros a leer, en orden de rowid, y la linea del plan que lo explica. */
    record Access(Iterable<Long> rowIds, String description) {
    }

    /** Una condicion que el indice puede responder. */
    private record Probe(Index index, Lookup op, List<Expr> constants, Expr condition) {
    }

    private final Database db;
    private final Analysis analysis;
    private final Evaluator evaluator;

    AccessPlanner(Database db, Analysis analysis, Evaluator evaluator) {
        this.db = db;
        this.analysis = analysis;
        this.evaluator = evaluator;
    }

    /**
     * @param table      la tabla a leer
     * @param name       como se llama en la consulta (alias o nombre)
     * @param source     su posicion en el FROM/JOIN
     * @param conditions los terminos AND disponibles para filtrarla
     * @param frame      marco con los registros ya fijados de las tablas anteriores
     */
    Access plan(Table table, String name, int source, List<Expr> conditions, Frame frame) {
        Probe probe = choose(table, source, conditions);
        if (probe == null) {
            return new Access(table.rowIds(), name + ": recorrido completo ("
                    + table.size() + " registros)");
        }

        DataType type = table.schema().column(probe.index().column()).type();
        Set<Long> ids = new TreeSet<>();
        for (Expr constant : probe.constants()) {
            // Un NULL nunca cumple una comparacion: no aporta registros.
            Object value = Values.coerce(evaluator.eval(constant, frame), type);
            if (value != null) {
                ids.addAll(probe.index().find(probe.op(), value));
            }
        }

        String description = name + ": indice " + probe.index().definition().name()
                + " (" + SqlText.of(probe.condition()) + ")";
        boolean literals = probe.constants().stream().allMatch(c -> c instanceof Literal);
        description += literals
                ? " -> " + ids.size() + " de " + table.size() + " registros"
                : ", una busqueda por cada registro externo";
        return new Access(ids, description);
    }

    /** Separa una condicion en sus terminos unidos por AND. */
    static List<Expr> conjuncts(Expr condition) {
        List<Expr> result = new ArrayList<>();
        if (condition instanceof Binary b && b.op() == BinaryOp.AND) {
            result.addAll(conjuncts(b.left()));
            result.addAll(conjuncts(b.right()));
        } else if (condition != null) {
            result.add(condition);
        }
        return result;
    }

    private Probe choose(Table table, int source, List<Expr> conditions) {
        Probe range = null;
        for (Expr condition : conditions) {
            Probe probe = probe(table, source, condition);
            if (probe != null && probe.op() == Lookup.EQ) {
                return probe;
            }
            if (range == null) {
                range = probe;
            }
        }
        return range;
    }

    private Probe probe(Table table, int source, Expr condition) {
        if (condition instanceof InList in && !in.negated()
                && in.values().stream().allMatch(v -> isConstant(v, source))) {
            return withIndex(table, source, in.operand(), Lookup.EQ, in.values(), condition);
        }
        if (!(condition instanceof Binary b) || !b.op().isComparison() || b.op() == BinaryOp.NEQ) {
            return null;
        }
        Lookup op = Lookup.valueOf(b.op().name());
        if (isConstant(b.right(), source)) {
            Probe probe = withIndex(table, source, b.left(), op, List.of(b.right()), condition);
            if (probe != null) {
                return probe;
            }
        }
        if (isConstant(b.left(), source)) {
            // 18 <= edad se busca como edad >= 18.
            return withIndex(table, source, b.right(), flip(op), List.of(b.left()), condition);
        }
        return null;
    }

    /** El probe si {@code expr} es una columna de esta tabla que tiene indice. */
    private Probe withIndex(Table table, int source, Expr expr, Lookup op,
                            List<Expr> constants, Expr condition) {
        if (!(expr instanceof ColumnRef ref)) {
            return null;
        }
        ColumnBinding b = analysis.binding(ref);
        if (b.depth() != 0 || b.source() != source) {
            return null;
        }
        Index index = db.indexOn(table.schema().name(), table.schema().column(b.column()).name());
        return index == null ? null : new Probe(index, op, constants, condition);
    }

    /** Si la expresion ya tiene valor cuando se lee la tabla {@code source}. */
    private boolean isConstant(Expr expr, int source) {
        if (expr instanceof Literal) {
            return true;
        }
        if (expr instanceof ColumnRef ref) {
            ColumnBinding b = analysis.binding(ref);
            return b.depth() > 0 || b.source() < source;
        }
        return false;
    }

    private static Lookup flip(Lookup op) {
        return switch (op) {
            case LT -> Lookup.GT;
            case LTE -> Lookup.GTE;
            case GT -> Lookup.LT;
            case GTE -> Lookup.LTE;
            default -> op;
        };
    }
}
