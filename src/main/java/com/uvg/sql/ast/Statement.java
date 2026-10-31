package com.uvg.sql.ast;

import com.uvg.sql.types.DataType;
import java.util.List;

/**
 * Sentencias del AST. A diferencia del arbol de parseo, aqui ya no hay
 * parentesis, comas ni palabras clave: solo lo que la sentencia significa.
 *
 * <p>Los campos opcionales ({@code where}, {@code having}, alias...) son
 * {@code null} cuando no aparecen; las listas opcionales quedan vacias.
 */
public sealed interface Statement {

    Pos pos();

    record CreateTable(Pos pos, Ident table, List<ColumnDef> columns) implements Statement {
    }

    record ColumnDef(Ident name, DataType type, boolean primaryKey, boolean notNull) {
    }

    record DropTable(Pos pos, Ident table) implements Statement {
    }

    record CreateIndex(Pos pos, Ident index, Ident table, Ident column) implements Statement {
    }

    record DropIndex(Pos pos, Ident index) implements Statement {
    }

    /** {@code columns} vacia significa "todas, en el orden del esquema". */
    record Insert(Pos pos, Ident table, List<Ident> columns, List<List<Expr>> rows)
            implements Statement {
    }

    record Update(Pos pos, Ident table, List<Assignment> assignments, Expr where)
            implements Statement {
    }

    record Assignment(Ident column, Expr value) {
    }

    record Delete(Pos pos, Ident table, Expr where) implements Statement {
    }

    /** SELECT es tambien la forma de las subconsultas. */
    record Select(Pos pos,
                  List<SelectItem> items,
                  TableRef from,
                  List<Join> joins,
                  Expr where,
                  List<Expr> groupBy,
                  Expr having,
                  List<OrderItem> orderBy) implements Statement {
    }

    sealed interface SelectItem {
    }

    /** {@code *} o {@code t.*}; {@code qualifier} es null en el primer caso. */
    record AllColumns(Pos pos, Ident qualifier) implements SelectItem {
    }

    /** Una expresion con su alias opcional ({@code AS nombre}). */
    record ExprItem(Expr expr, Ident alias) implements SelectItem {
    }

    /** Una tabla del FROM o de un JOIN. Sin alias, se la nombra por la tabla. */
    record TableRef(Ident table, Ident alias) {

        public String effectiveName() {
            return alias != null ? alias.name() : table.name();
        }
    }

    record Join(Pos pos, TableRef table, Expr on) {
    }

    record OrderItem(Expr expr, boolean descending) {
    }
}
