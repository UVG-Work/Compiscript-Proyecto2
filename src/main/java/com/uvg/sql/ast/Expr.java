package com.uvg.sql.ast;

import com.uvg.sql.ast.Statement.Select;
import com.uvg.sql.types.DataType;
import java.util.List;

/**
 * Expresiones del AST.
 *
 * <p>Ojo: son records, asi que dos nodos iguales en texto y posicion serian
 * {@code equals}. Toda informacion que se le asocie a un nodo (tipo, columna
 * resuelta...) se guarda en mapas por identidad, nunca por igualdad.
 */
public sealed interface Expr {

    Pos pos();

    /**
     * Un valor constante. {@code value} es Long, Double, String, Boolean,
     * LocalDate o null, segun {@code type}.
     */
    record Literal(Pos pos, Object value, DataType type) implements Expr {
    }

    /** {@code columna} o {@code tabla.columna}; {@code qualifier} null si no se califica. */
    record ColumnRef(Pos pos, String qualifier, String name) implements Expr {

        public String display() {
            return qualifier == null ? name : qualifier + "." + name;
        }
    }

    enum UnaryOp {
        NEG("-"), NOT("NOT");

        public final String symbol;

        UnaryOp(String symbol) {
            this.symbol = symbol;
        }
    }

    record Unary(Pos pos, UnaryOp op, Expr operand) implements Expr {
    }

    enum BinaryOp {
        ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%"),
        EQ("="), NEQ("<>"), LT("<"), LTE("<="), GT(">"), GTE(">="),
        AND("AND"), OR("OR");

        public final String symbol;

        BinaryOp(String symbol) {
            this.symbol = symbol;
        }

        public boolean isArithmetic() {
            return ordinal() <= MOD.ordinal();
        }

        public boolean isComparison() {
            return ordinal() >= EQ.ordinal() && ordinal() <= GTE.ordinal();
        }

        public boolean isLogical() {
            return this == AND || this == OR;
        }
    }

    record Binary(Pos pos, BinaryOp op, Expr left, Expr right) implements Expr {
    }

    record IsNull(Pos pos, Expr operand, boolean negated) implements Expr {
    }

    record InList(Pos pos, Expr operand, List<Expr> values, boolean negated) implements Expr {
    }

    record InSubquery(Pos pos, Expr operand, Select query, boolean negated) implements Expr {
    }

    record Exists(Pos pos, Select query, boolean negated) implements Expr {
    }

    /** Subconsulta usada como valor: debe dar una columna y a lo mas un registro. */
    record ScalarSubquery(Pos pos, Select query) implements Expr {
    }

    enum AggregateFunc {
        COUNT, SUM, AVG, MIN, MAX
    }

    /** {@code argument} null representa {@code COUNT(*)}. */
    record Aggregate(Pos pos, AggregateFunc func, Expr argument) implements Expr {
    }
}
