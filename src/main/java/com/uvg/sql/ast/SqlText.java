package com.uvg.sql.ast;

import com.uvg.sql.ast.Expr.Aggregate;
import com.uvg.sql.ast.Expr.Binary;
import com.uvg.sql.ast.Expr.ColumnRef;
import com.uvg.sql.ast.Expr.Exists;
import com.uvg.sql.ast.Expr.InList;
import com.uvg.sql.ast.Expr.InSubquery;
import com.uvg.sql.ast.Expr.IsNull;
import com.uvg.sql.ast.Expr.Literal;
import com.uvg.sql.ast.Expr.ScalarSubquery;
import com.uvg.sql.ast.Expr.Unary;
import com.uvg.sql.types.Values;
import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * Devuelve una expresion a texto SQL. Da el nombre de las columnas sin alias
 * en los resultados ({@code SELECT COUNT(*)} produce la columna "COUNT(*)") y
 * cita la expresion en los mensajes de error. Las subconsultas se abrevian.
 */
public final class SqlText {

    private SqlText() {
    }

    public static String of(Expr expr) {
        if (expr instanceof Literal e) {
            if (e.value() instanceof String text) {
                return "'" + text.replace("'", "''") + "'";
            }
            if (e.value() instanceof LocalDate date) {
                return "DATE '" + date + "'";
            }
            return Values.display(e.value());
        }
        if (expr instanceof ColumnRef e) {
            return e.display();
        }
        if (expr instanceof Unary e) {
            return e.op() == Expr.UnaryOp.NOT ? "NOT " + of(e.operand()) : "-" + of(e.operand());
        }
        if (expr instanceof Binary e) {
            return of(e.left()) + " " + e.op().symbol + " " + of(e.right());
        }
        if (expr instanceof IsNull e) {
            return of(e.operand()) + (e.negated() ? " IS NOT NULL" : " IS NULL");
        }
        if (expr instanceof InList e) {
            return of(e.operand()) + (e.negated() ? " NOT IN (" : " IN (")
                    + e.values().stream().map(SqlText::of).collect(Collectors.joining(", ")) + ")";
        }
        if (expr instanceof InSubquery e) {
            return of(e.operand()) + (e.negated() ? " NOT IN (SELECT ...)" : " IN (SELECT ...)");
        }
        if (expr instanceof Exists e) {
            return (e.negated() ? "NOT EXISTS" : "EXISTS") + " (SELECT ...)";
        }
        if (expr instanceof ScalarSubquery) {
            return "(SELECT ...)";
        }
        Aggregate e = (Aggregate) expr;
        return e.func() + "(" + (e.argument() == null ? "*" : of(e.argument())) + ")";
    }
}
