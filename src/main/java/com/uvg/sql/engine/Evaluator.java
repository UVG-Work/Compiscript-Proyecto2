package com.uvg.sql.engine;

import com.uvg.sql.ast.Expr;
import com.uvg.sql.ast.Expr.Aggregate;
import com.uvg.sql.ast.Expr.Binary;
import com.uvg.sql.ast.Expr.BinaryOp;
import com.uvg.sql.ast.Expr.ColumnRef;
import com.uvg.sql.ast.Expr.Exists;
import com.uvg.sql.ast.Expr.InList;
import com.uvg.sql.ast.Expr.InSubquery;
import com.uvg.sql.ast.Expr.IsNull;
import com.uvg.sql.ast.Expr.Literal;
import com.uvg.sql.ast.Expr.ScalarSubquery;
import com.uvg.sql.ast.Expr.Unary;
import com.uvg.sql.ast.Statement.Select;
import com.uvg.sql.semantic.Analysis;
import com.uvg.sql.semantic.Analysis.ColumnBinding;
import com.uvg.sql.types.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Evalua expresiones sobre un {@link Frame}.
 *
 * <p>NULL sigue la logica de tres valores de SQL: una comparacion con NULL da
 * NULL (desconocido), {@code FALSE AND NULL} es FALSE, {@code TRUE OR NULL} es
 * TRUE, y WHERE solo conserva los registros donde la condicion es TRUE.
 */
final class Evaluator {

    private final Analysis analysis;
    /** Ejecuta una subconsulta con el marco externo; lo provee el {@link Executor}. */
    private final BiFunction<Select, Frame, List<Object[]>> subquery;

    Evaluator(Analysis analysis, BiFunction<Select, Frame, List<Object[]>> subquery) {
        this.analysis = analysis;
        this.subquery = subquery;
    }

    static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value);
    }

    Object eval(Expr expr, Frame frame) {
        if (expr instanceof Literal e) {
            return e.value();
        }
        if (expr instanceof ColumnRef e) {
            ColumnBinding b = analysis.binding(e);
            return frame.up(b.depth()).rows()[b.source()][b.column()];
        }
        if (expr instanceof Unary e) {
            Object value = eval(e.operand(), frame);
            if (value == null) {
                return null;
            }
            if (e.op() == Expr.UnaryOp.NOT) {
                return !(Boolean) value;
            }
            return value instanceof Long n ? (Object) negate(n, e) : (Object) (-(Double) value);
        }
        if (expr instanceof Binary e) {
            return binary(e, frame);
        }
        if (expr instanceof IsNull e) {
            return (eval(e.operand(), frame) == null) != e.negated();
        }
        if (expr instanceof InList e) {
            Object value = eval(e.operand(), frame);
            boolean sawNull = false;
            for (Expr candidate : e.values()) {
                Object other = eval(candidate, frame);
                if (value != null && other != null && Values.equal(value, other)) {
                    return !e.negated();
                }
                sawNull |= other == null;
            }
            return value == null || sawNull ? null : e.negated();
        }
        if (expr instanceof Aggregate e) {
            return aggregate(e, frame);
        }
        if (expr instanceof InSubquery e) {
            return inSubquery(e, frame);
        }
        if (expr instanceof Exists e) {
            return subquery.apply(e.query(), frame).isEmpty() == e.negated();
        }
        if (expr instanceof ScalarSubquery e) {
            List<Object[]> rows = subquery.apply(e.query(), frame);
            if (rows.size() > 1) {
                throw new ExecutionException(e.pos(), "la subconsulta escalar devolvio "
                        + rows.size() + " registros; debe devolver a lo mas uno");
            }
            return rows.isEmpty() ? null : rows.get(0)[0];
        }
        throw new IllegalStateException("expresion sin evaluar: " + expr);
    }

    /**
     * {@code x IN (SELECT ...)} con la misma logica de NULL que una lista: si no
     * hay coincidencia pero la subconsulta trae algun NULL, el resultado es
     * desconocido. Por eso {@code NOT IN} con un NULL en la subconsulta no
     * devuelve nada, igual que en cualquier motor SQL.
     */
    private Object inSubquery(InSubquery e, Frame frame) {
        Object value = eval(e.operand(), frame);
        List<Object[]> rows = subquery.apply(e.query(), frame);
        if (rows.isEmpty()) {
            return e.negated();
        }
        if (value == null) {
            return null;
        }
        boolean sawNull = false;
        for (Object[] row : rows) {
            if (row[0] == null) {
                sawNull = true;
            } else if (Values.equal(value, row[0])) {
                return !e.negated();
            }
        }
        return sawNull ? null : e.negated();
    }

    /**
     * Calcula una agregacion sobre las combinaciones del grupo. Como en SQL,
     * se ignoran los NULL; sobre un grupo sin valores COUNT da 0 y las demas
     * dan NULL.
     */
    private Object aggregate(Aggregate e, Frame frame) {
        List<Object[][]> group = frame.group();
        if (e.argument() == null) {
            return (long) group.size();
        }
        List<Object> values = new ArrayList<>();
        for (Object[][] rows : group) {
            Object value = eval(e.argument(), new Frame(frame.parent(), rows));
            if (value != null) {
                values.add(value);
            }
        }
        if (e.func() == Expr.AggregateFunc.COUNT) {
            return (long) values.size();
        }
        if (values.isEmpty()) {
            return null;
        }
        return switch (e.func()) {
            case SUM -> sum(e, values);
            case AVG -> values.stream().mapToDouble(v -> ((Number) v).doubleValue()).sum()
                    / values.size();
            case MIN -> values.stream().min(Values::compare).orElseThrow();
            default -> values.stream().max(Values::compare).orElseThrow();
        };
    }

    /** SUM de INT es INT (y avisa si se desborda); si hay algun FLOAT, es FLOAT. */
    private static Object sum(Aggregate e, List<Object> values) {
        if (values.stream().allMatch(v -> v instanceof Long)) {
            long total = 0;
            try {
                for (Object v : values) {
                    total = Math.addExact(total, (Long) v);
                }
            } catch (ArithmeticException overflow) {
                throw new ExecutionException(e.pos(), "SUM no cabe en un INT de 64 bits");
            }
            return total;
        }
        return values.stream().mapToDouble(v -> ((Number) v).doubleValue()).sum();
    }

    private Object binary(Binary e, Frame frame) {
        if (e.op().isLogical()) {
            return logical(e.op(), eval(e.left(), frame), () -> eval(e.right(), frame));
        }
        Object left = eval(e.left(), frame);
        Object right = eval(e.right(), frame);
        if (left == null || right == null) {
            return null;
        }
        if (e.op().isComparison()) {
            int c = Values.compare(left, right);
            return switch (e.op()) {
                case EQ -> c == 0;
                case NEQ -> c != 0;
                case LT -> c < 0;
                case LTE -> c <= 0;
                case GT -> c > 0;
                default -> c >= 0;
            };
        }
        return arithmetic(e, (Number) left, (Number) right);
    }

    /**
     * AND y OR con cortocircuito: si el lado izquierdo ya decide, el derecho no
     * se evalua (puede ser una subconsulta cara).
     */
    private static Object logical(BinaryOp op, Object left, java.util.function.Supplier<Object> right) {
        Boolean decisive = op == BinaryOp.AND ? Boolean.FALSE : Boolean.TRUE;
        if (decisive.equals(left)) {
            return decisive;
        }
        Object other = right.get();
        if (decisive.equals(other)) {
            return decisive;
        }
        return left == null || other == null ? null : !decisive;
    }

    /** INT con INT da INT (la division trunca); si alguno es FLOAT, da FLOAT. */
    private static Object arithmetic(Binary e, Number left, Number right) {
        if (left instanceof Long a && right instanceof Long b) {
            try {
                return switch (e.op()) {
                    case ADD -> Math.addExact(a, b);
                    case SUB -> Math.subtractExact(a, b);
                    case MUL -> Math.multiplyExact(a, b);
                    case DIV -> b == 0 ? divisionByZero(e) : a / b;
                    default -> b == 0 ? divisionByZero(e) : a % b;
                };
            } catch (ArithmeticException overflow) {
                throw new ExecutionException(e.pos(), "el resultado de " + e.op().symbol
                        + " no cabe en un INT de 64 bits");
            }
        }
        double a = left.doubleValue();
        double b = right.doubleValue();
        return switch (e.op()) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> b == 0 ? divisionByZero(e) : a / b;
            default -> b == 0 ? divisionByZero(e) : a % b;
        };
    }

    private static long negate(long value, Unary e) {
        if (value == Long.MIN_VALUE) {
            throw new ExecutionException(e.pos(), "el resultado de - no cabe en un INT de 64 bits");
        }
        return -value;
    }

    private static <T> T divisionByZero(Binary e) {
        throw new ExecutionException(e.pos(), "division entre cero");
    }
}
