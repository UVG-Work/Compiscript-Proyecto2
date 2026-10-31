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
import com.uvg.sql.ast.Statement.AllColumns;
import com.uvg.sql.ast.Statement.ColumnDef;
import com.uvg.sql.ast.Statement.CreateIndex;
import com.uvg.sql.ast.Statement.CreateTable;
import com.uvg.sql.ast.Statement.Delete;
import com.uvg.sql.ast.Statement.DropIndex;
import com.uvg.sql.ast.Statement.DropTable;
import com.uvg.sql.ast.Statement.ExprItem;
import com.uvg.sql.ast.Statement.Insert;
import com.uvg.sql.ast.Statement.Join;
import com.uvg.sql.ast.Statement.OrderItem;
import com.uvg.sql.ast.Statement.Select;
import com.uvg.sql.ast.Statement.TableRef;
import com.uvg.sql.ast.Statement.Update;
import com.uvg.sql.types.Values;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.tree.Tree;

/**
 * Vista del AST para mostrarlo: cada nodo es una etiqueta y sus hijos.
 *
 * <p>Implementa {@link Tree} de ANTLR para que el IDE lo dibuje con el mismo
 * {@code TreeViewer} que el arbol de parseo. {@link #render()} da la version en
 * texto que imprime el CLI con {@code --ast}.
 */
public final class AstView implements Tree {

    private final String label;
    private final List<AstView> children = new ArrayList<>();
    private AstView parent;

    private AstView(String label) {
        this.label = label;
    }

    /** Raiz "script" con una rama por sentencia. */
    public static AstView of(List<Statement> statements) {
        AstView root = new AstView("Script");
        statements.forEach(statement -> root.add(of(statement)));
        return root;
    }

    public static AstView of(Statement statement) {
        if (statement instanceof CreateTable s) {
            return node("CreateTable " + s.table(),
                    s.columns().stream().map(AstView::column).toList());
        }
        if (statement instanceof DropTable s) {
            return leaf("DropTable " + s.table());
        }
        if (statement instanceof CreateIndex s) {
            return leaf("CreateIndex " + s.index() + " ON " + s.table() + "(" + s.column() + ")");
        }
        if (statement instanceof DropIndex s) {
            return leaf("DropIndex " + s.index());
        }
        if (statement instanceof Insert s) {
            return insert(s);
        }
        if (statement instanceof Update s) {
            return update(s);
        }
        if (statement instanceof Delete s) {
            return node("Delete " + s.table(), optional("Where", s.where()));
        }
        return select((Select) statement);
    }

    public static AstView of(Expr expr) {
        if (expr instanceof Literal e) {
            return leaf(literal(e));
        }
        if (expr instanceof ColumnRef e) {
            return leaf("Columna " + e.display());
        }
        if (expr instanceof Unary e) {
            return node(e.op().symbol, List.of(of(e.operand())));
        }
        if (expr instanceof Binary e) {
            return node(e.op().symbol, List.of(of(e.left()), of(e.right())));
        }
        if (expr instanceof IsNull e) {
            return node(e.negated() ? "IS NOT NULL" : "IS NULL", List.of(of(e.operand())));
        }
        if (expr instanceof InList e) {
            return node(e.negated() ? "NOT IN" : "IN", List.of(of(e.operand()),
                    node("Lista", e.values().stream().map(AstView::of).toList())));
        }
        if (expr instanceof InSubquery e) {
            return node(e.negated() ? "NOT IN" : "IN", List.of(of(e.operand()), select(e.query())));
        }
        if (expr instanceof Exists e) {
            return node(e.negated() ? "NOT EXISTS" : "EXISTS", List.of(select(e.query())));
        }
        if (expr instanceof ScalarSubquery e) {
            return node("Subconsulta escalar", List.of(select(e.query())));
        }
        Aggregate e = (Aggregate) expr;
        return e.argument() == null
                ? leaf(e.func() + "(*)")
                : node(e.func().name(), List.of(of(e.argument())));
    }

    /** Las cadenas entre comillas y las fechas con DATE, para distinguirlas de numeros. */
    private static String literal(Literal literal) {
        Object value = literal.value();
        if (value instanceof String text) {
            return "'" + text + "'";
        }
        if (value instanceof java.time.LocalDate date) {
            return "DATE '" + date + "'";
        }
        return Values.display(value);
    }

    // ------------------------------------------------------------------ nodos

    private static AstView column(ColumnDef column) {
        String constraints = (column.primaryKey() ? " PRIMARY KEY" : "")
                + (column.notNull() ? " NOT NULL" : "");
        return leaf("Columna " + column.name() + " " + column.type() + constraints);
    }

    private static AstView insert(Insert insert) {
        List<AstView> children = new ArrayList<>();
        if (!insert.columns().isEmpty()) {
            children.add(leaf("Columnas " + insert.columns()));
        }
        for (List<Expr> row : insert.rows()) {
            children.add(node("Valores", row.stream().map(AstView::of).toList()));
        }
        return node("Insert " + insert.table(), children);
    }

    private static AstView update(Update update) {
        List<AstView> children = new ArrayList<>();
        for (Statement.Assignment assignment : update.assignments()) {
            children.add(node("Set " + assignment.column(), List.of(of(assignment.value()))));
        }
        children.addAll(optional("Where", update.where()));
        return node("Update " + update.table(), children);
    }

    private static AstView select(Select select) {
        List<AstView> children = new ArrayList<>();
        List<AstView> items = new ArrayList<>();
        for (Statement.SelectItem item : select.items()) {
            if (item instanceof AllColumns all) {
                items.add(leaf(all.qualifier() == null ? "*" : all.qualifier() + ".*"));
            } else {
                ExprItem e = (ExprItem) item;
                items.add(e.alias() == null ? of(e.expr()) : node("AS " + e.alias(), List.of(of(e.expr()))));
            }
        }
        children.add(node("Proyeccion", items));
        children.add(node("From", List.of(table(select.from()))));
        for (Join join : select.joins()) {
            children.add(node("InnerJoin", List.of(table(join.table()), node("On", List.of(of(join.on()))))));
        }
        children.addAll(optional("Where", select.where()));
        if (!select.groupBy().isEmpty()) {
            children.add(node("GroupBy", select.groupBy().stream().map(AstView::of).toList()));
        }
        children.addAll(optional("Having", select.having()));
        if (!select.orderBy().isEmpty()) {
            List<AstView> order = new ArrayList<>();
            for (OrderItem item : select.orderBy()) {
                order.add(node(item.descending() ? "DESC" : "ASC", List.of(of(item.expr()))));
            }
            children.add(node("OrderBy", order));
        }
        return node("Select", children);
    }

    private static AstView table(TableRef ref) {
        return leaf("Tabla " + ref.table() + (ref.alias() == null ? "" : " AS " + ref.alias()));
    }

    private static List<AstView> optional(String label, Expr expr) {
        return expr == null ? List.of() : List.of(node(label, List.of(of(expr))));
    }

    private static AstView leaf(String label) {
        return new AstView(label);
    }

    private static AstView node(String label, List<AstView> children) {
        AstView view = new AstView(label);
        children.forEach(view::add);
        return view;
    }

    private void add(AstView child) {
        child.parent = this;
        children.add(child);
    }

    // ------------------------------------------------------------------ salida

    /** Arbol con sangria, una linea por nodo. */
    public String render() {
        StringBuilder out = new StringBuilder();
        render(out, "", "");
        return out.toString();
    }

    private void render(StringBuilder out, String prefix, String childPrefix) {
        out.append(prefix).append(label).append('\n');
        for (int i = 0; i < children.size(); i++) {
            boolean last = i == children.size() - 1;
            children.get(i).render(out, childPrefix + (last ? "└─ " : "├─ "),
                    childPrefix + (last ? "   " : "│  "));
        }
    }

    // ------------------------------------------------- org.antlr.v4.runtime.tree.Tree

    @Override
    public Tree getParent() {
        return parent;
    }

    /** TreeViewer pinta {@code getPayload().toString()} en los nodos que no son de ANTLR. */
    @Override
    public Object getPayload() {
        return label;
    }

    @Override
    public Tree getChild(int i) {
        return children.get(i);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public String toStringTree() {
        return render();
    }

    @Override
    public String toString() {
        return label;
    }
}
