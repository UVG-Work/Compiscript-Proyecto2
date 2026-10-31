package com.uvg.sql.semantic;

import com.uvg.sql.ast.Expr;
import com.uvg.sql.ast.Expr.Aggregate;
import com.uvg.sql.ast.Expr.AggregateFunc;
import com.uvg.sql.ast.Expr.Binary;
import com.uvg.sql.ast.Expr.ColumnRef;
import com.uvg.sql.ast.Expr.Exists;
import com.uvg.sql.ast.Expr.InList;
import com.uvg.sql.ast.Expr.InSubquery;
import com.uvg.sql.ast.Expr.IsNull;
import com.uvg.sql.ast.Expr.Literal;
import com.uvg.sql.ast.Expr.ScalarSubquery;
import com.uvg.sql.ast.Expr.Unary;
import com.uvg.sql.ast.Ident;
import com.uvg.sql.ast.Pos;
import com.uvg.sql.ast.SqlText;
import com.uvg.sql.ast.Statement;
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
import com.uvg.sql.catalog.Catalog;
import com.uvg.sql.catalog.Column;
import com.uvg.sql.catalog.IndexDef;
import com.uvg.sql.catalog.TableSchema;
import com.uvg.sql.diagnostics.Diagnostic;
import com.uvg.sql.diagnostics.Diagnostic.Phase;
import com.uvg.sql.semantic.Analysis.ColumnBinding;
import com.uvg.sql.semantic.Analysis.Output;
import com.uvg.sql.semantic.Analysis.QueryShape;
import com.uvg.sql.semantic.Analysis.Source;
import com.uvg.sql.types.DataType;
import com.uvg.sql.types.DataType.Kind;
import com.uvg.sql.types.Values;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analisis semantico: verifica todo el script antes de ejecutar nada.
 *
 * <p>Trabaja sobre una copia del catalogo y le aplica los CREATE y DROP que
 * van pasando el analisis, de modo que {@code CREATE TABLE t ...; INSERT INTO
 * t ...} se valida sin haber creado la tabla todavia.
 *
 * <p>Cuando una expresion ya tiene un error, su tipo queda en {@code null} y
 * las expresiones que la contienen no reportan nada mas: un solo error por
 * causa, sin cascadas.
 */
public final class SemanticAnalyzer {

    /** Clausula en la que se analiza una expresion: decide si admite agregaciones. */
    private enum Clause {
        SELECT("SELECT", true), WHERE("WHERE", false), ON("ON", false),
        GROUP_BY("GROUP BY", false), HAVING("HAVING", true), ORDER_BY("ORDER BY", true),
        VALUES("VALUES", false), SET("SET", false);

        final String label;
        final boolean allowsAggregates;

        Clause(String label, boolean allowsAggregates) {
            this.label = label;
            this.allowsAggregates = allowsAggregates;
        }
    }

    /**
     * Las tablas visibles en una consulta. {@code parent} es la consulta que la
     * contiene, para las subconsultas correlacionadas. {@code broken} indica
     * que alguna tabla del FROM no existe: ya se reporto y no tiene caso
     * reportar ademas cada columna que se busque en ella.
     */
    private static final class Scope {
        final Scope parent;
        final List<Source> sources = new ArrayList<>();
        boolean broken;

        Scope(Scope parent) {
            this.parent = parent;
        }

        int sourceNamed(String name) {
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).name().equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        boolean anyBroken() {
            for (Scope s = this; s != null; s = s.parent) {
                if (s.broken) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Catalog catalog;
    private final Analysis analysis = new Analysis();
    private final List<Diagnostic> errors = new ArrayList<>();
    /** Los SELECT que se estan analizando, del mas interno (arriba) al principal. */
    private final Deque<Select> selects = new ArrayDeque<>();

    /** @param catalog el catalogo real; no se modifica, se trabaja sobre una copia. */
    public SemanticAnalyzer(Catalog catalog) {
        this.catalog = catalog.copy();
    }

    /** @return los errores de todo el script, en el orden en que aparecen. */
    public List<Diagnostic> analyze(List<Statement> statements) {
        for (Statement statement : statements) {
            int before = errors.size();
            check(statement);
            if (errors.size() == before) {
                applyDefinition(statement);
            }
        }
        return errors;
    }

    public Analysis analysis() {
        return analysis;
    }

    /** El esquema que crea un CREATE TABLE; lo comparten el analisis y la ejecucion. */
    public static TableSchema schemaOf(CreateTable create) {
        List<Column> columns = new ArrayList<>();
        for (ColumnDef def : create.columns()) {
            columns.add(new Column(def.name().name(), def.type(), def.primaryKey(), def.notNull()));
        }
        return new TableSchema(create.table().name(), columns);
    }

    // ---------------------------------------------------------------- sentencias

    private void check(Statement statement) {
        if (statement instanceof CreateTable s) {
            createTable(s);
        } else if (statement instanceof DropTable s) {
            existingTable(s.table());
        } else if (statement instanceof CreateIndex s) {
            createIndex(s);
        } else if (statement instanceof DropIndex s) {
            if (catalog.index(s.index().name()) == null) {
                error(s.index().pos(), "no existe el indice '%s'", s.index());
            }
        } else if (statement instanceof Insert s) {
            insert(s);
        } else if (statement instanceof Update s) {
            update(s);
        } else if (statement instanceof Delete s) {
            delete(s);
        } else {
            select((Select) statement, null);
        }
    }

    /** Refleja en la copia del catalogo una sentencia de definicion ya validada. */
    private void applyDefinition(Statement statement) {
        if (statement instanceof CreateTable s) {
            catalog.addTable(schemaOf(s));
        } else if (statement instanceof DropTable s) {
            catalog.removeTable(s.table().name());
        } else if (statement instanceof CreateIndex s) {
            catalog.addIndex(new IndexDef(s.index().name(), s.table().name(), s.column().name()));
        } else if (statement instanceof DropIndex s) {
            catalog.removeIndex(s.index().name());
        }
    }

    private void createTable(CreateTable create) {
        if (catalog.table(create.table().name()) != null) {
            error(create.table().pos(), "la tabla '%s' ya existe", create.table());
        }
        Set<String> seen = new HashSet<>();
        int primaryKeys = 0;
        for (ColumnDef column : create.columns()) {
            if (!seen.add(column.name().name())) {
                error(column.name().pos(), "la columna '%s' esta repetida en la tabla '%s'",
                        column.name(), create.table());
            }
            if (column.primaryKey() && ++primaryKeys == 2) {
                error(column.name().pos(), "la tabla '%s' tiene mas de una PRIMARY KEY",
                        create.table());
            }
        }
    }

    private void createIndex(CreateIndex create) {
        IndexDef existing = catalog.index(create.index().name());
        if (existing != null) {
            error(create.index().pos(), "el indice '%s' ya existe (sobre %s.%s)",
                    create.index(), existing.table(), existing.column());
            return;
        }
        TableSchema table = existingTable(create.table());
        if (table == null) {
            return;
        }
        if (table.indexOf(create.column().name()) < 0) {
            error(create.column().pos(), "la columna '%s' no existe en la tabla '%s'",
                    create.column(), create.table());
            return;
        }
        IndexDef same = catalog.indexOn(table.name(), create.column().name());
        if (same != null) {
            error(create.index().pos(), "la columna %s.%s ya tiene el indice '%s'",
                    table.name(), create.column(), same.name());
        }
    }

    private void insert(Insert insert) {
        TableSchema table = existingTable(insert.table());
        if (table == null) {
            return;
        }

        // A que columna va cada valor: todas en orden, o las que se listaron.
        List<Integer> targets = new ArrayList<>();
        if (insert.columns().isEmpty()) {
            for (int i = 0; i < table.columns().size(); i++) {
                targets.add(i);
            }
        } else {
            for (Ident name : insert.columns()) {
                int index = table.indexOf(name.name());
                if (index < 0) {
                    error(name.pos(), "la columna '%s' no existe en la tabla '%s'", name, table.name());
                    return;
                }
                if (targets.contains(index)) {
                    error(name.pos(), "la columna '%s' aparece dos veces en el INSERT", name);
                    return;
                }
                targets.add(index);
            }
            for (int i = 0; i < table.columns().size(); i++) {
                Column column = table.column(i);
                if (!targets.contains(i) && !column.acceptsNull()) {
                    error(insert.table().pos(), "la columna '%s' es %s y el INSERT no le da valor",
                            column.name(), column.primaryKey() ? "PRIMARY KEY" : "NOT NULL");
                }
            }
        }

        Scope values = new Scope(null);
        for (int r = 0; r < insert.rows().size(); r++) {
            List<Expr> row = insert.rows().get(r);
            if (row.size() != targets.size()) {
                error(row.get(0).pos(), "la fila %d del INSERT tiene %d valor(es) y se esperaban %d",
                        r + 1, row.size(), targets.size());
                continue;
            }
            for (int i = 0; i < row.size(); i++) {
                Expr value = row.get(i);
                assignable(table.column(targets.get(i)), value, typeOf(value, values, Clause.VALUES));
            }
        }
    }

    private void update(Update update) {
        TableSchema table = existingTable(update.table());
        if (table == null) {
            return;
        }
        Scope scope = new Scope(null);
        scope.sources.add(new Source(table.name(), table));

        Set<String> seen = new HashSet<>();
        for (Assignment assignment : update.assignments()) {
            Ident name = assignment.column();
            int index = table.indexOf(name.name());
            if (index < 0) {
                error(name.pos(), "la columna '%s' no existe en la tabla '%s'", name, table.name());
                continue;
            }
            if (!seen.add(name.name())) {
                error(name.pos(), "la columna '%s' se asigna dos veces en el UPDATE", name);
                continue;
            }
            assignable(table.column(index), assignment.value(),
                    typeOf(assignment.value(), scope, Clause.SET));
        }
        condition(update.where(), scope, Clause.WHERE);
    }

    private void delete(Delete delete) {
        TableSchema table = existingTable(delete.table());
        if (table == null) {
            return;
        }
        Scope scope = new Scope(null);
        scope.sources.add(new Source(table.name(), table));
        condition(delete.where(), scope, Clause.WHERE);
    }

    // ------------------------------------------------------------------- SELECT

    /**
     * Analiza un SELECT (consulta principal o subconsulta) y registra su forma.
     *
     * @return la forma, o null si alguna tabla del FROM no existe
     */
    private QueryShape select(Select select, Scope parent) {
        selects.push(select);
        try {
            return analyzeSelect(select, parent);
        } finally {
            selects.pop();
        }
    }

    private QueryShape analyzeSelect(Select select, Scope parent) {
        Scope scope = new Scope(parent);
        addSource(scope, select.from());
        for (Join join : select.joins()) {
            addSource(scope, join.table());
            // ON solo ve las tablas unidas hasta este punto, como en SQL estandar.
            condition(join.on(), scope, Clause.ON);
        }
        condition(select.where(), scope, Clause.WHERE);
        for (Expr key : select.groupBy()) {
            typeOf(key, scope, Clause.GROUP_BY);
        }

        boolean grouped = !select.groupBy().isEmpty() || select.having() != null
                || select.items().stream().anyMatch(item ->
                        item instanceof ExprItem e && containsAggregate(e.expr()))
                || select.orderBy().stream().anyMatch(item -> containsAggregate(item.expr()));

        List<Output> outputs = new ArrayList<>();
        List<Boolean> explicitAlias = new ArrayList<>();
        for (SelectItem item : select.items()) {
            if (item instanceof AllColumns all) {
                expandStar(all, scope, outputs, explicitAlias);
            } else {
                ExprItem e = (ExprItem) item;
                DataType type = typeOf(e.expr(), scope, Clause.SELECT);
                String name = e.alias() != null ? e.alias().name()
                        : e.expr() instanceof ColumnRef ref ? ref.name() : SqlText.of(e.expr());
                outputs.add(new Output(name, type, e.expr()));
                explicitAlias.add(e.alias() != null);
            }
        }

        condition(select.having(), scope, Clause.HAVING);

        List<Integer> orderTargets = new ArrayList<>();
        for (OrderItem item : select.orderBy()) {
            // ORDER BY 2 (por posicion) no esta soportado; ordenar por la
            // constante 2 dejaria el orden igual sin avisar, asi que se rechaza.
            if (item.expr() instanceof Literal) {
                error(item.expr().pos(), "ORDER BY por posicion o constante no esta soportado;"
                        + " use el nombre o el alias de la columna");
            }
            int target = aliasTarget(item.expr(), outputs, explicitAlias);
            if (target < 0) {
                typeOf(item.expr(), scope, Clause.ORDER_BY);
            }
            orderTargets.add(target);
        }

        if (grouped && !scope.broken) {
            List<String> keys = select.groupBy().stream().map(this::key).toList();
            outputs.forEach(output -> checkGrouped(output.expr(), keys, "SELECT"));
            if (select.having() != null) {
                checkGrouped(select.having(), keys, "HAVING");
            }
            for (int i = 0; i < select.orderBy().size(); i++) {
                if (orderTargets.get(i) < 0) {
                    checkGrouped(select.orderBy().get(i).expr(), keys, "ORDER BY");
                }
            }
        }

        QueryShape shape = new QueryShape(List.copyOf(scope.sources), outputs, grouped, orderTargets);
        analysis.shape(select, shape);
        return scope.broken ? null : shape;
    }

    private void addSource(Scope scope, TableRef ref) {
        TableSchema table = existingTable(ref.table());
        if (table == null) {
            scope.broken = true;
            return;
        }
        String name = ref.effectiveName();
        if (scope.sourceNamed(name) >= 0) {
            Ident at = ref.alias() != null ? ref.alias() : ref.table();
            error(at.pos(), "el nombre '%s' se usa dos veces en el FROM; use un alias distinto", name);
            return;
        }
        scope.sources.add(new Source(name, table));
    }

    /** Expande {@code *} o {@code t.*} en una referencia por columna, ya resuelta. */
    private void expandStar(AllColumns all, Scope scope, List<Output> outputs,
                            List<Boolean> explicitAlias) {
        List<Integer> sources = new ArrayList<>();
        if (all.qualifier() == null) {
            for (int i = 0; i < scope.sources.size(); i++) {
                sources.add(i);
            }
        } else {
            int index = scope.sourceNamed(all.qualifier().name());
            if (index < 0) {
                if (!scope.broken) {
                    error(all.pos(), "la tabla o alias '%s' no aparece en el FROM", all.qualifier());
                }
                return;
            }
            sources.add(index);
        }
        for (int s : sources) {
            Source source = scope.sources.get(s);
            for (int c = 0; c < source.schema().columns().size(); c++) {
                Column column = source.schema().column(c);
                ColumnRef ref = new ColumnRef(all.pos(), source.name(), column.name());
                analysis.bind(ref, new ColumnBinding(0, s, c, column.type()));
                outputs.add(new Output(column.name(), column.type(), ref));
                explicitAlias.add(false);
            }
        }
    }

    /** ORDER BY total, donde total es el alias de una columna del SELECT. */
    private static int aliasTarget(Expr expr, List<Output> outputs, List<Boolean> explicitAlias) {
        if (expr instanceof ColumnRef ref && ref.qualifier() == null) {
            for (int i = 0; i < outputs.size(); i++) {
                if (explicitAlias.get(i) && outputs.get(i).name().equals(ref.name())) {
                    return i;
                }
            }
        }
        return -1;
    }

    // -------------------------------------------------------------- GROUP BY

    /**
     * En una consulta agrupada cada columna (de esta consulta) debe estar en
     * GROUP BY o dentro de una agregacion: si no, su valor no esta definido
     * para el grupo.
     */
    private void checkGrouped(Expr expr, List<String> groupKeys, String clause) {
        if (groupKeys.contains(key(expr)) || expr instanceof Aggregate || expr instanceof Literal) {
            return;
        }
        if (expr instanceof ColumnRef ref) {
            ColumnBinding binding = analysis.binding(ref);
            if (binding != null && binding.depth() == 0) {
                error(ref.pos(), "la columna '%s' del %s debe aparecer en GROUP BY o usarse"
                        + " dentro de una funcion de agregacion", ref.display(), clause);
            }
            return;
        }
        // Las subconsultas son otra consulta: no se revisan contra este GROUP BY.
        for (Expr child : children(expr)) {
            checkGrouped(child, groupKeys, clause);
        }
    }

    /**
     * Forma canonica de una expresion para compararla con las de GROUP BY. Las
     * columnas se comparan por la columna a la que resuelven, asi {@code e.id}
     * e {@code id} son la misma clave si apuntan a lo mismo.
     */
    private String key(Expr expr) {
        if (expr instanceof ColumnRef ref) {
            ColumnBinding b = analysis.binding(ref);
            return b == null ? ref.display() : "#" + b.depth() + "." + b.source() + "." + b.column();
        }
        if (expr instanceof Unary e) {
            return e.op() + "(" + key(e.operand()) + ")";
        }
        if (expr instanceof Binary e) {
            return "(" + key(e.left()) + " " + e.op() + " " + key(e.right()) + ")";
        }
        if (expr instanceof Aggregate e) {
            return e.func() + "(" + (e.argument() == null ? "*" : key(e.argument())) + ")";
        }
        if (expr instanceof Literal) {
            return SqlText.of(expr);
        }
        return "@" + System.identityHashCode(expr);
    }

    private static boolean containsAggregate(Expr expr) {
        if (expr instanceof Aggregate) {
            return true;
        }
        for (Expr child : children(expr)) {
            if (containsAggregate(child)) {
                return true;
            }
        }
        return false;
    }

    /** Subexpresiones directas, sin entrar a subconsultas. */
    private static List<Expr> children(Expr expr) {
        if (expr instanceof Unary e) {
            return List.of(e.operand());
        }
        if (expr instanceof Binary e) {
            return List.of(e.left(), e.right());
        }
        if (expr instanceof IsNull e) {
            return List.of(e.operand());
        }
        if (expr instanceof InList e) {
            List<Expr> all = new ArrayList<>(e.values());
            all.add(0, e.operand());
            return all;
        }
        if (expr instanceof InSubquery e) {
            return List.of(e.operand());
        }
        if (expr instanceof Aggregate e && e.argument() != null) {
            return List.of(e.argument());
        }
        return List.of();
    }

    // ------------------------------------------------------------- expresiones

    /** Una condicion de WHERE, ON o HAVING: debe ser BOOLEAN. */
    private void condition(Expr expr, Scope scope, Clause clause) {
        if (expr == null) {
            return;
        }
        DataType type = typeOf(expr, scope, clause);
        if (type != null && !type.isNull() && type.kind() != Kind.BOOLEAN) {
            error(expr.pos(), "la condicion de %s debe ser BOOLEAN y '%s' es %s",
                    clause.label, SqlText.of(expr), describe(type));
        }
    }

    private DataType typeOf(Expr expr, Scope scope, Clause clause) {
        return typeOf(expr, scope, clause, false);
    }

    /** @return el tipo de la expresion, o null si tiene un error ya reportado. */
    private DataType typeOf(Expr expr, Scope scope, Clause clause, boolean insideAggregate) {
        if (expr instanceof Literal e) {
            return e.type();
        }
        if (expr instanceof ColumnRef e) {
            return column(e, scope, clause);
        }
        if (expr instanceof Unary e) {
            DataType type = typeOf(e.operand(), scope, clause, insideAggregate);
            if (type == null || type.isNull()) {
                return type;
            }
            boolean ok = e.op() == Expr.UnaryOp.NEG ? type.isNumeric() : type.kind() == Kind.BOOLEAN;
            if (!ok) {
                error(e.pos(), "el operador %s no se aplica a %s", e.op().symbol, describe(type));
                return null;
            }
            return type;
        }
        if (expr instanceof Binary e) {
            return binary(e, typeOf(e.left(), scope, clause, insideAggregate),
                    typeOf(e.right(), scope, clause, insideAggregate));
        }
        if (expr instanceof IsNull e) {
            typeOf(e.operand(), scope, clause, insideAggregate);
            return DataType.BOOLEAN;
        }
        if (expr instanceof InList e) {
            DataType operand = typeOf(e.operand(), scope, clause, insideAggregate);
            for (Expr value : e.values()) {
                comparable(value.pos(), e, e.operand(), operand, value,
                        typeOf(value, scope, clause, insideAggregate));
            }
            return DataType.BOOLEAN;
        }
        if (expr instanceof InSubquery e) {
            DataType operand = typeOf(e.operand(), scope, clause, insideAggregate);
            QueryShape shape = select(e.query(), scope);
            if (shape != null && singleColumn(shape, e.query(), "de IN")) {
                comparable(e.pos(), e, e.operand(), operand, null, shape.outputs().get(0).type());
            }
            return DataType.BOOLEAN;
        }
        if (expr instanceof Exists e) {
            select(e.query(), scope);
            return DataType.BOOLEAN;
        }
        if (expr instanceof ScalarSubquery e) {
            QueryShape shape = select(e.query(), scope);
            if (shape == null || !singleColumn(shape, e.query(), "escalar")) {
                return null;
            }
            return shape.outputs().get(0).type();
        }
        return aggregate((Aggregate) expr, scope, clause, insideAggregate);
    }

    private DataType aggregate(Aggregate e, Scope scope, Clause clause, boolean insideAggregate) {
        if (!clause.allowsAggregates) {
            error(e.pos(), "no se permite la funcion de agregacion %s en %s", e.func(), clause.label);
        } else if (insideAggregate) {
            error(e.pos(), "no se pueden anidar funciones de agregacion (%s)", SqlText.of(e));
        }
        if (e.argument() == null) {
            if (e.func() != AggregateFunc.COUNT) {
                error(e.pos(), "solo COUNT admite *; %s necesita una columna", e.func());
                return null;
            }
            return DataType.INT;
        }
        DataType arg = typeOf(e.argument(), scope, clause, true);
        if (arg == null) {
            return e.func() == AggregateFunc.COUNT ? DataType.INT : null;
        }
        switch (e.func()) {
            case COUNT:
                return DataType.INT;
            case SUM:
            case AVG:
                if (!arg.isNumeric() && !arg.isNull()) {
                    error(e.pos(), "%s necesita un argumento numerico y '%s' es %s",
                            e.func(), SqlText.of(e.argument()), describe(arg));
                    return null;
                }
                return e.func() == AggregateFunc.AVG ? DataType.FLOAT
                        : arg.kind() == Kind.FLOAT ? DataType.FLOAT : DataType.INT;
            default:
                if (arg.kind() == Kind.BOOLEAN) {
                    error(e.pos(), "%s no se aplica a BOOLEAN", e.func());
                    return null;
                }
                return arg;
        }
    }

    private DataType binary(Binary e, DataType left, DataType right) {
        if (left == null || right == null) {
            return e.op().isArithmetic() ? null : DataType.BOOLEAN;
        }
        if (e.op().isArithmetic()) {
            if ((!left.isNumeric() && !left.isNull()) || (!right.isNumeric() && !right.isNull())) {
                error(e.pos(), "el operador %s necesita numeros y recibe %s y %s",
                        e.op().symbol, describe(left), describe(right));
                return null;
            }
            return left.kind() == Kind.FLOAT || right.kind() == Kind.FLOAT
                    ? DataType.FLOAT : DataType.INT;
        }
        if (e.op().isLogical()) {
            for (DataType side : List.of(left, right)) {
                if (side.kind() != Kind.BOOLEAN && !side.isNull()) {
                    error(e.pos(), "%s necesita operandos BOOLEAN y recibe %s",
                            e.op().symbol, describe(side));
                    return null;
                }
            }
            return DataType.BOOLEAN;
        }
        comparable(e.pos(), e, e.left(), left, e.right(), right);
        return DataType.BOOLEAN;
    }

    /**
     * Si dos valores se pueden comparar. Numeros entre si, cada tipo consigo
     * mismo, NULL con todo, y una fecha con un texto literal que sea una
     * fecha valida ({@code fecha >= '2024-01-01'}).
     */
    private void comparable(Pos pos, Expr whole, Expr leftExpr, DataType left, Expr rightExpr,
                            DataType right) {
        if (left == null || right == null || left.isNull() || right.isNull()
                || (left.isNumeric() && right.isNumeric()) || left.kind() == right.kind()) {
            return;
        }
        if (left.kind() == Kind.DATE && isTextLiteral(rightExpr)) {
            validDate(rightExpr);
            return;
        }
        if (right.kind() == Kind.DATE && isTextLiteral(leftExpr)) {
            validDate(leftExpr);
            return;
        }
        error(pos, "tipos incompatibles: no se puede comparar %s con %s en %s",
                describe(left), describe(right), SqlText.of(whole));
    }

    /** Que un valor de tipo {@code type} se pueda guardar en la columna. */
    private void assignable(Column column, Expr value, DataType type) {
        if (type == null) {
            return;
        }
        if (type.isNull()) {
            if (!column.acceptsNull()) {
                error(value.pos(), "la columna '%s' es %s y no admite NULL", column.name(),
                        column.primaryKey() ? "PRIMARY KEY" : "NOT NULL");
            }
            return;
        }
        DataType target = column.type();
        boolean ok = switch (target.kind()) {
            case INT -> type.kind() == Kind.INT;
            case FLOAT -> type.isNumeric();
            case DATE -> type.kind() == Kind.DATE || isTextLiteral(value);
            default -> type.kind() == target.kind();
        };
        if (!ok) {
            error(value.pos(), "tipos incompatibles: la columna '%s' es %s y el valor %s es %s",
                    column.name(), target, SqlText.of(value), describe(type));
            return;
        }
        if (target.kind() == Kind.DATE && isTextLiteral(value)) {
            validDate(value);
        }
        if (target.kind() == Kind.VARCHAR && value instanceof Literal literal
                && literal.value() instanceof String text && text.length() > target.length()) {
            error(value.pos(), "el texto de %d caracteres no cabe en la columna '%s' %s",
                    text.length(), column.name(), target);
        }
    }

    private void validDate(Expr textLiteral) {
        String text = (String) ((Literal) textLiteral).value();
        if (Values.parseDate(text) == null) {
            error(textLiteral.pos(), "'%s' no es una fecha valida; DATE usa el formato YYYY-MM-DD", text);
        }
    }

    private static boolean isTextLiteral(Expr expr) {
        return expr instanceof Literal literal && literal.value() instanceof String;
    }

    private boolean singleColumn(QueryShape shape, Select query, String kind) {
        if (shape.outputs().size() != 1) {
            error(query.pos(), "la subconsulta %s debe devolver una sola columna y devuelve %d",
                    kind, shape.outputs().size());
            return false;
        }
        return true;
    }

    // ----------------------------------------------------------------- nombres

    /**
     * Resuelve una columna buscando de la consulta actual hacia afuera. Un
     * nombre sin calificar que esta en dos tablas del mismo nivel es ambiguo.
     */
    private DataType column(ColumnRef ref, Scope scope, Clause clause) {
        if (clause == Clause.VALUES && scope.parent == null && scope.sources.isEmpty()) {
            error(ref.pos(), "VALUES solo admite valores, no la columna '%s'", ref.display());
            return null;
        }
        int depth = 0;
        for (Scope s = scope; s != null; s = s.parent, depth++) {
            if (ref.qualifier() != null) {
                int source = s.sourceNamed(ref.qualifier());
                if (source < 0) {
                    continue;
                }
                TableSchema table = s.sources.get(source).schema();
                int column = table.indexOf(ref.name());
                if (column < 0) {
                    error(ref.pos(), "la columna '%s' no existe en la tabla '%s'",
                            ref.name(), table.name());
                    return null;
                }
                return bind(ref, depth, source, table);
            }
            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < s.sources.size(); i++) {
                if (s.sources.get(i).schema().indexOf(ref.name()) >= 0) {
                    matches.add(i);
                }
            }
            if (matches.size() > 1) {
                Scope level = s;
                error(ref.pos(), "la columna '%s' es ambigua: existe en %s; califiquela, por"
                                + " ejemplo %s.%s", ref.name(),
                        matches.stream().map(i -> level.sources.get(i).name())
                                .collect(Collectors.joining(" y ")),
                        level.sources.get(matches.get(0)).name(), ref.name());
                return null;
            }
            if (matches.size() == 1) {
                return bind(ref, depth, matches.get(0), s.sources.get(matches.get(0)).schema());
            }
        }
        if (scope.anyBroken()) {
            return null;
        }
        if (ref.qualifier() != null) {
            error(ref.pos(), "la tabla o alias '%s' no aparece en el FROM", ref.qualifier());
        } else {
            error(ref.pos(), "la columna '%s' no existe en %s", ref.name(),
                    scope.sources.stream().map(Source::name).collect(Collectors.joining(", ")));
        }
        return null;
    }

    private DataType bind(ColumnRef ref, int depth, int source, TableSchema table) {
        // Una columna de "depth" niveles hacia afuera hace que las "depth"
        // consultas mas internas dependan del registro externo.
        selects.stream().limit(depth).forEach(analysis::markCorrelated);
        int column = table.indexOf(ref.name());
        DataType type = table.column(column).type();
        analysis.bind(ref, new ColumnBinding(depth, source, column, type));
        return type;
    }

    private TableSchema existingTable(Ident name) {
        TableSchema table = catalog.table(name.name());
        if (table == null) {
            error(name.pos(), "la tabla '%s' no existe", name);
        }
        return table;
    }

    /** Un VARCHAR de un valor se nombra sin longitud: la de un literal no dice nada. */
    private static String describe(DataType type) {
        return type.kind() == Kind.VARCHAR ? "VARCHAR" : type.toString();
    }

    private void error(Pos pos, String format, Object... args) {
        errors.add(new Diagnostic(Phase.SEMANTICO, pos.line(), pos.column(),
                String.format(format, args)));
    }
}
