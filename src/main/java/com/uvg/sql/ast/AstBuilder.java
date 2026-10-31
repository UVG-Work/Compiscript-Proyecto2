package com.uvg.sql.ast;

import com.uvg.sql.ast.Expr.Aggregate;
import com.uvg.sql.ast.Expr.AggregateFunc;
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
import com.uvg.sql.ast.Expr.UnaryOp;
import com.uvg.sql.ast.Statement.AllColumns;
import com.uvg.sql.ast.Statement.Assignment;
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
import com.uvg.sql.ast.Statement.SelectItem;
import com.uvg.sql.ast.Statement.TableRef;
import com.uvg.sql.ast.Statement.Update;
import com.uvg.sql.diagnostics.Diagnostic;
import com.uvg.sql.diagnostics.Diagnostic.Phase;
import com.uvg.sql.parser.SqlBaseVisitor;
import com.uvg.sql.parser.SqlParser;
import com.uvg.sql.types.DataType;
import com.uvg.sql.types.Values;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.Token;

/**
 * Recorre el arbol de parseo y construye el AST.
 *
 * <p>Aqui se descartan los detalles de sintaxis (parentesis, comas, palabras
 * clave), se normalizan los identificadores a minusculas, se convierten los
 * literales a su valor Java y se pliegan formas equivalentes: {@code NOT
 * EXISTS} queda como un {@link Exists} negado y el {@code +} unario desaparece.
 *
 * <p>Los literales que no se pueden representar (un entero fuera de rango, una
 * fecha imposible) se reportan como errores semanticos en {@link #errors()}.
 */
public final class AstBuilder extends SqlBaseVisitor<Object> {

    private final List<Diagnostic> errors = new ArrayList<>();

    public List<Statement> build(SqlParser.ScriptContext script) {
        List<Statement> statements = new ArrayList<>();
        for (SqlParser.StatementContext statement : script.statement()) {
            statements.add((Statement) visit(statement.getChild(0)));
        }
        return statements;
    }

    public List<Diagnostic> errors() {
        return errors;
    }

    // ---------------------------------------------------------------- sentencias

    @Override
    public CreateTable visitCreateTable(SqlParser.CreateTableContext ctx) {
        List<ColumnDef> columns = new ArrayList<>();
        for (SqlParser.ColumnDefContext column : ctx.columnDef()) {
            boolean primaryKey = column.columnConstraint().stream()
                    .anyMatch(c -> c instanceof SqlParser.PrimaryKeyConstraintContext);
            boolean notNull = column.columnConstraint().stream()
                    .anyMatch(c -> c instanceof SqlParser.NotNullConstraintContext);
            columns.add(new ColumnDef(ident(column.identifier()),
                    (DataType) visit(column.dataType()), primaryKey, notNull));
        }
        return new CreateTable(Pos.of(ctx), ident(ctx.identifier()), columns);
    }

    @Override
    public DataType visitIntType(SqlParser.IntTypeContext ctx) {
        return DataType.INT;
    }

    @Override
    public DataType visitFloatType(SqlParser.FloatTypeContext ctx) {
        return DataType.FLOAT;
    }

    @Override
    public DataType visitBooleanType(SqlParser.BooleanTypeContext ctx) {
        return DataType.BOOLEAN;
    }

    @Override
    public DataType visitDateType(SqlParser.DateTypeContext ctx) {
        return DataType.DATE;
    }

    @Override
    public DataType visitVarcharType(SqlParser.VarcharTypeContext ctx) {
        Token token = ctx.INTEGER_LIT().getSymbol();
        Long length = parseInteger(token);
        if (length == null || length < 1 || length > Integer.MAX_VALUE) {
            if (length != null) {
                error(token, "la longitud de VARCHAR debe estar entre 1 y " + Integer.MAX_VALUE);
            }
            return DataType.varchar(1);
        }
        return DataType.varchar(length.intValue());
    }

    @Override
    public DropTable visitDropTable(SqlParser.DropTableContext ctx) {
        return new DropTable(Pos.of(ctx), ident(ctx.identifier()));
    }

    @Override
    public CreateIndex visitCreateIndex(SqlParser.CreateIndexContext ctx) {
        return new CreateIndex(Pos.of(ctx), ident(ctx.identifier(0)),
                ident(ctx.identifier(1)), ident(ctx.identifier(2)));
    }

    @Override
    public DropIndex visitDropIndex(SqlParser.DropIndexContext ctx) {
        return new DropIndex(Pos.of(ctx), ident(ctx.identifier()));
    }

    @Override
    public Insert visitInsertStmt(SqlParser.InsertStmtContext ctx) {
        // identifier(0) es la tabla; los demas, la lista de columnas.
        List<Ident> columns = ctx.identifier().stream().skip(1).map(this::ident).toList();
        List<List<Expr>> rows = new ArrayList<>();
        for (SqlParser.ValuesRowContext row : ctx.valuesRow()) {
            rows.add(exprs(row.expr()));
        }
        return new Insert(Pos.of(ctx), ident(ctx.identifier(0)), columns, rows);
    }

    @Override
    public Update visitUpdateStmt(SqlParser.UpdateStmtContext ctx) {
        List<Assignment> assignments = new ArrayList<>();
        for (SqlParser.AssignmentContext assignment : ctx.assignment()) {
            assignments.add(new Assignment(ident(assignment.identifier()), expr(assignment.expr())));
        }
        return new Update(Pos.of(ctx), ident(ctx.identifier()), assignments,
                ctx.whereClause() == null ? null : expr(ctx.whereClause().expr()));
    }

    @Override
    public Delete visitDeleteStmt(SqlParser.DeleteStmtContext ctx) {
        return new Delete(Pos.of(ctx), ident(ctx.identifier()),
                ctx.whereClause() == null ? null : expr(ctx.whereClause().expr()));
    }

    @Override
    public Select visitSelectStmt(SqlParser.SelectStmtContext ctx) {
        List<SelectItem> items = new ArrayList<>();
        for (SqlParser.SelectItemContext item : ctx.selectItem()) {
            items.add((SelectItem) visit(item));
        }
        List<Join> joins = new ArrayList<>();
        for (SqlParser.JoinClauseContext join : ctx.joinClause()) {
            joins.add(new Join(Pos.of(join), tableRef(join.tableRef()), expr(join.expr())));
        }
        // Las expresiones de GROUP BY y HAVING vienen todas en ctx.expr(): las
        // de GROUP BY primero y, si hay HAVING, la suya al final.
        List<Expr> grouped = exprs(ctx.expr());
        Expr having = null;
        if (ctx.HAVING() != null) {
            having = grouped.remove(grouped.size() - 1);
        }
        List<OrderItem> orderBy = new ArrayList<>();
        for (SqlParser.OrderItemContext order : ctx.orderItem()) {
            orderBy.add(new OrderItem(expr(order.expr()), order.DESC() != null));
        }
        return new Select(Pos.of(ctx), items, tableRef(ctx.tableRef()), joins,
                ctx.whereClause() == null ? null : expr(ctx.whereClause().expr()),
                grouped, having, orderBy);
    }

    @Override
    public SelectItem visitAllColumns(SqlParser.AllColumnsContext ctx) {
        return new AllColumns(Pos.of(ctx), null);
    }

    @Override
    public SelectItem visitTableColumns(SqlParser.TableColumnsContext ctx) {
        return new AllColumns(Pos.of(ctx), ident(ctx.identifier()));
    }

    @Override
    public SelectItem visitExprItem(SqlParser.ExprItemContext ctx) {
        return new ExprItem(expr(ctx.expr()),
                ctx.identifier() == null ? null : ident(ctx.identifier()));
    }

    // --------------------------------------------------------------- expresiones

    @Override
    public Expr visitPrimaryExpr(SqlParser.PrimaryExprContext ctx) {
        return (Expr) visit(ctx.primary());
    }

    @Override
    public Expr visitUnaryExpr(SqlParser.UnaryExprContext ctx) {
        Expr operand = expr(ctx.expr());
        if (ctx.op.getType() == SqlParser.PLUS) {
            return operand;
        }
        // -18 es un literal, no una operacion: asi el planificador lo reconoce
        // como constante al decidir si usa un indice.
        if (operand instanceof Literal literal && literal.value() instanceof Long n) {
            return new Literal(Pos.of(ctx), -n, DataType.INT);
        }
        if (operand instanceof Literal literal && literal.value() instanceof Double d) {
            return new Literal(Pos.of(ctx), -d, DataType.FLOAT);
        }
        return new Unary(Pos.of(ctx), UnaryOp.NEG, operand);
    }

    @Override
    public Expr visitNotExpr(SqlParser.NotExprContext ctx) {
        Expr operand = expr(ctx.expr());
        // NOT EXISTS (...) se representa como un solo nodo, como lo lee una persona.
        if (operand instanceof Exists exists) {
            return new Exists(Pos.of(ctx), exists.query(), !exists.negated());
        }
        return new Unary(Pos.of(ctx), UnaryOp.NOT, operand);
    }

    @Override
    public Expr visitMulExpr(SqlParser.MulExprContext ctx) {
        BinaryOp op = switch (ctx.op.getType()) {
            case SqlParser.STAR -> BinaryOp.MUL;
            case SqlParser.SLASH -> BinaryOp.DIV;
            default -> BinaryOp.MOD;
        };
        return binary(ctx.op, op, ctx.expr(0), ctx.expr(1));
    }

    @Override
    public Expr visitAddExpr(SqlParser.AddExprContext ctx) {
        BinaryOp op = ctx.op.getType() == SqlParser.PLUS ? BinaryOp.ADD : BinaryOp.SUB;
        return binary(ctx.op, op, ctx.expr(0), ctx.expr(1));
    }

    @Override
    public Expr visitCompareExpr(SqlParser.CompareExprContext ctx) {
        BinaryOp op = switch (ctx.op.getType()) {
            case SqlParser.EQ -> BinaryOp.EQ;
            case SqlParser.NEQ -> BinaryOp.NEQ;
            case SqlParser.LT -> BinaryOp.LT;
            case SqlParser.LTE -> BinaryOp.LTE;
            case SqlParser.GT -> BinaryOp.GT;
            default -> BinaryOp.GTE;
        };
        return binary(ctx.op, op, ctx.expr(0), ctx.expr(1));
    }

    @Override
    public Expr visitAndExpr(SqlParser.AndExprContext ctx) {
        return binary(ctx.AND().getSymbol(), BinaryOp.AND, ctx.expr(0), ctx.expr(1));
    }

    @Override
    public Expr visitOrExpr(SqlParser.OrExprContext ctx) {
        return binary(ctx.OR().getSymbol(), BinaryOp.OR, ctx.expr(0), ctx.expr(1));
    }

    @Override
    public Expr visitIsNullExpr(SqlParser.IsNullExprContext ctx) {
        return new IsNull(Pos.of(ctx.IS().getSymbol()), expr(ctx.expr()), ctx.NOT() != null);
    }

    @Override
    public Expr visitInSubqueryExpr(SqlParser.InSubqueryExprContext ctx) {
        return new InSubquery(Pos.of(ctx.IN().getSymbol()), expr(ctx.expr()),
                visitSelectStmt(ctx.selectStmt()), ctx.NOT() != null);
    }

    @Override
    public Expr visitInListExpr(SqlParser.InListExprContext ctx) {
        List<Expr> all = exprs(ctx.expr());
        return new InList(Pos.of(ctx.IN().getSymbol()), all.get(0),
                all.subList(1, all.size()), ctx.NOT() != null);
    }

    @Override
    public Expr visitLiteralPrimary(SqlParser.LiteralPrimaryContext ctx) {
        return (Expr) visit(ctx.literal());
    }

    @Override
    public Expr visitAggregatePrimary(SqlParser.AggregatePrimaryContext ctx) {
        SqlParser.AggregateContext aggregate = ctx.aggregate();
        AggregateFunc func = AggregateFunc.valueOf(
                aggregate.func.getText().toUpperCase(Locale.ROOT));
        return new Aggregate(Pos.of(ctx), func,
                aggregate.expr() == null ? null : expr(aggregate.expr()));
    }

    @Override
    public Expr visitExistsPrimary(SqlParser.ExistsPrimaryContext ctx) {
        return new Exists(Pos.of(ctx), visitSelectStmt(ctx.selectStmt()), false);
    }

    @Override
    public Expr visitSubqueryPrimary(SqlParser.SubqueryPrimaryContext ctx) {
        return new ScalarSubquery(Pos.of(ctx), visitSelectStmt(ctx.selectStmt()));
    }

    @Override
    public Expr visitParenPrimary(SqlParser.ParenPrimaryContext ctx) {
        return expr(ctx.expr());
    }

    @Override
    public Expr visitColumnPrimary(SqlParser.ColumnPrimaryContext ctx) {
        if (ctx.identifier().size() == 2) {
            return new ColumnRef(Pos.of(ctx), name(ctx.identifier(0)), name(ctx.identifier(1)));
        }
        return new ColumnRef(Pos.of(ctx), null, name(ctx.identifier(0)));
    }

    @Override
    public Expr visitIntLiteral(SqlParser.IntLiteralContext ctx) {
        Long value = parseInteger(ctx.INTEGER_LIT().getSymbol());
        return new Literal(Pos.of(ctx), value, value == null ? DataType.NULL : DataType.INT);
    }

    @Override
    public Expr visitFloatLiteral(SqlParser.FloatLiteralContext ctx) {
        return new Literal(Pos.of(ctx), Double.parseDouble(ctx.getText()), DataType.FLOAT);
    }

    @Override
    public Expr visitStringLiteral(SqlParser.StringLiteralContext ctx) {
        String text = unquote(ctx.STRING().getText());
        return new Literal(Pos.of(ctx), text, DataType.varchar(text.length()));
    }

    @Override
    public Expr visitDateLiteral(SqlParser.DateLiteralContext ctx) {
        String text = unquote(ctx.STRING().getText());
        LocalDate date = Values.parseDate(text);
        if (date == null) {
            error(ctx.STRING().getSymbol(),
                    "fecha invalida '" + text + "': se espera el formato YYYY-MM-DD");
            return new Literal(Pos.of(ctx), null, DataType.NULL);
        }
        return new Literal(Pos.of(ctx), date, DataType.DATE);
    }

    @Override
    public Expr visitBoolLiteral(SqlParser.BoolLiteralContext ctx) {
        return new Literal(Pos.of(ctx), ctx.TRUE() != null, DataType.BOOLEAN);
    }

    @Override
    public Expr visitNullLiteral(SqlParser.NullLiteralContext ctx) {
        return new Literal(Pos.of(ctx), null, DataType.NULL);
    }

    // ------------------------------------------------------------------ apoyo

    private Expr expr(SqlParser.ExprContext ctx) {
        return (Expr) visit(ctx);
    }

    private List<Expr> exprs(List<SqlParser.ExprContext> contexts) {
        List<Expr> result = new ArrayList<>();
        for (SqlParser.ExprContext ctx : contexts) {
            result.add(expr(ctx));
        }
        return result;
    }

    /** La posicion de un binario es la de su operador: ahi apunta un error de tipos. */
    private Expr binary(Token op, BinaryOp kind, SqlParser.ExprContext left,
                        SqlParser.ExprContext right) {
        return new Binary(Pos.of(op), kind, expr(left), expr(right));
    }

    private TableRef tableRef(SqlParser.TableRefContext ctx) {
        return new TableRef(ident(ctx.identifier(0)),
                ctx.identifier().size() > 1 ? ident(ctx.identifier(1)) : null);
    }

    private Ident ident(SqlParser.IdentifierContext ctx) {
        return new Ident(name(ctx), Pos.of(ctx));
    }

    private static String name(SqlParser.IdentifierContext ctx) {
        return ctx.getText().toLowerCase(Locale.ROOT);
    }

    /** Quita las comillas de los extremos y deshace el escape {@code ''}. */
    private static String unquote(String quoted) {
        return quoted.substring(1, quoted.length() - 1).replace("''", "'");
    }

    private Long parseInteger(Token token) {
        try {
            return Long.parseLong(token.getText());
        } catch (NumberFormatException e) {
            error(token, "el entero " + token.getText() + " no cabe en un INT de 64 bits");
            return null;
        }
    }

    private void error(Token token, String message) {
        errors.add(new Diagnostic(Phase.SEMANTICO, token.getLine(),
                token.getCharPositionInLine(), message));
    }
}
