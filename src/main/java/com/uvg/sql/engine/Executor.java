package com.uvg.sql.engine;

import com.uvg.sql.ast.Expr;
import com.uvg.sql.ast.Pos;
import com.uvg.sql.ast.Statement;
import com.uvg.sql.ast.Statement.CreateIndex;
import com.uvg.sql.ast.Statement.Assignment;
import com.uvg.sql.ast.Statement.CreateTable;
import com.uvg.sql.ast.Statement.Delete;
import com.uvg.sql.ast.Statement.DropIndex;
import com.uvg.sql.ast.Statement.DropTable;
import com.uvg.sql.ast.Statement.Insert;
import com.uvg.sql.ast.Statement.Join;
import com.uvg.sql.ast.Statement.OrderItem;
import com.uvg.sql.ast.Statement.Select;
import com.uvg.sql.ast.Statement.Update;
import com.uvg.sql.catalog.Column;
import com.uvg.sql.catalog.IndexDef;
import com.uvg.sql.catalog.TableSchema;
import com.uvg.sql.semantic.Analysis;
import com.uvg.sql.semantic.Analysis.Output;
import com.uvg.sql.semantic.Analysis.QueryShape;
import com.uvg.sql.semantic.SemanticAnalyzer;
import com.uvg.sql.storage.Database;
import com.uvg.sql.storage.Table;
import com.uvg.sql.types.DataType.Kind;
import com.uvg.sql.types.Values;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ejecuta sentencias ya analizadas contra una {@link Database}.
 *
 * <p>Cada sentencia que modifica datos es atomica: primero calcula y valida
 * todos los registros nuevos (tipos, NOT NULL, llave primaria) y solo si todo
 * es valido los aplica y guarda. Un error a la mitad no deja la tabla a medias.
 */
public final class Executor {

    private final Database db;
    private final Analysis analysis;
    private final Evaluator evaluator;

    private final AccessPlanner planner;

    /**
     * Lineas del plan de la sentencia en curso. Una subconsulta correlacionada
     * o una tabla interna de un JOIN se leen muchas veces, pero su linea del
     * plan se anota una sola: {@code noted} recuerda que ya se anoto, por
     * SELECT y por posicion de tabla (-1 para la linea de la subconsulta).
     */
    private List<String> plan = new ArrayList<>();
    private final Map<Select, Set<Integer>> noted = new IdentityHashMap<>();
    /** Resultados de las subconsultas no correlacionadas de la sentencia en curso. */
    private final Map<Select, List<Object[]>> cache = new IdentityHashMap<>();

    public Executor(Database db, Analysis analysis) {
        this.db = db;
        this.analysis = analysis;
        this.evaluator = new Evaluator(analysis, this::subquery);
        this.planner = new AccessPlanner(db, analysis, evaluator);
    }

    public StatementResult execute(Statement statement) {
        plan = new ArrayList<>();
        noted.clear();
        cache.clear();
        if (statement instanceof CreateTable s) {
            db.createTable(SemanticAnalyzer.schemaOf(s));
            return StatementResult.message("Tabla " + s.table() + " creada");
        }
        if (statement instanceof DropTable s) {
            db.dropTable(s.table().name());
            return StatementResult.message("Tabla " + s.table() + " eliminada");
        }
        if (statement instanceof CreateIndex s) {
            db.createIndex(new IndexDef(s.index().name(), s.table().name(), s.column().name()));
            return StatementResult.message("Indice " + s.index() + " creado sobre "
                    + s.table() + "(" + s.column() + ")");
        }
        if (statement instanceof DropIndex s) {
            db.dropIndex(s.index().name());
            return StatementResult.message("Indice " + s.index() + " eliminado");
        }
        if (statement instanceof Insert s) {
            return insert(s);
        }
        if (statement instanceof Update s) {
            return update(s);
        }
        if (statement instanceof Delete s) {
            return delete(s);
        }
        Select select = (Select) statement;
        List<Object[]> rows = query(select, null);
        List<String> columns = analysis.shape(select).outputs().stream().map(Output::name).toList();
        return new StatementResult(rows.size() + " fila(s)", columns, rows, plan);
    }

    // ------------------------------------------------------------------ SELECT

    /**
     * Ejecuta una subconsulta. Si no es correlacionada se ejecuta una vez y se
     * reutiliza: los datos no cambian durante la sentencia, porque UPDATE y
     * DELETE evaluan todo su WHERE antes de modificar nada.
     */
    private List<Object[]> subquery(Select select, Frame outer) {
        boolean correlated = analysis.isCorrelated(select);
        note(select, -1, "subconsulta de la linea " + select.pos().line() + (correlated
                ? ": correlacionada, se ejecuta por cada registro externo"
                : ": no correlacionada, se ejecuta una sola vez"));
        if (correlated) {
            return query(select, outer);
        }
        // get/put y no computeIfAbsent: la consulta puede tener subconsultas
        // propias que agregan al cache mientras se calcula.
        List<Object[]> rows = cache.get(select);
        if (rows == null) {
            rows = query(select, outer);
            cache.put(select, rows);
        }
        return rows;
    }

    /**
     * Ejecuta un SELECT y devuelve sus registros ya proyectados y ordenados.
     *
     * @param outer el marco de la consulta que la contiene, o null si es la
     *              consulta principal
     */
    List<Object[]> query(Select select, Frame outer) {
        QueryShape shape = analysis.shape(select);
        List<Object[][]> combinations = combine(select, shape, outer);

        // Cada "unidad" produce un registro del resultado: una combinacion de
        // registros o, si la consulta agrupa, un grupo entero.
        List<Frame> units = new ArrayList<>();
        if (shape.grouped()) {
            for (List<Object[][]> group : group(select, shape, combinations, outer)) {
                Object[][] first = group.isEmpty() ? new Object[shape.sources().size()][] : group.get(0);
                Frame frame = new Frame(outer, first, group);
                if (select.having() == null
                        || Evaluator.isTrue(evaluator.eval(select.having(), frame))) {
                    units.add(frame);
                }
            }
        } else {
            combinations.forEach(combination -> units.add(new Frame(outer, combination)));
        }

        List<Object[]> output = new ArrayList<>();
        List<Object[]> sortKeys = new ArrayList<>();
        for (Frame frame : units) {
            Object[] row = project(shape, frame);
            output.add(row);
            sortKeys.add(sortKeys(select, shape, row, frame));
        }
        return sort(select, output, sortKeys);
    }

    /**
     * Agrupa las combinaciones por los valores de GROUP BY, en el orden en que
     * aparece cada grupo por primera vez. Sin GROUP BY (solo agregaciones) toda
     * la consulta es un grupo, aunque este vacio: {@code SELECT COUNT(*)} de
     * una tabla vacia da 0, no ningun registro.
     */
    private List<List<Object[][]>> group(Select select, QueryShape shape,
                                         List<Object[][]> combinations, Frame outer) {
        if (select.groupBy().isEmpty()) {
            return List.of(combinations);
        }
        Map<List<Object>, List<Object[][]>> groups = new LinkedHashMap<>();
        for (Object[][] combination : combinations) {
            Frame frame = new Frame(outer, combination);
            List<Object> key = new ArrayList<>();
            for (Expr expr : select.groupBy()) {
                key.add(evaluator.eval(expr, frame));
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(combination);
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * Producto de las tablas del FROM/JOIN filtrado por cada ON y por WHERE,
     * con ciclos anidados: la tabla i se recorre una vez por cada combinacion
     * valida de las tablas 0..i-1.
     */
    private List<Object[][]> combine(Select select, QueryShape shape, Frame outer) {
        List<Object[][]> result = new ArrayList<>();
        nest(select, shape, 0, new Object[shape.sources().size()][], outer, result);
        return result;
    }

    /**
     * Fija el registro de la tabla {@code level} y baja a la siguiente. La
     * forma de leer cada tabla se decide aqui, con las anteriores ya fijadas:
     * asi un JOIN puede buscar en el indice de la tabla interna con el valor
     * del registro externo.
     */
    private void nest(Select select, QueryShape shape, int level, Object[][] current,
                      Frame outer, List<Object[][]> result) {
        Frame frame = new Frame(outer, current);
        Table table = db.table(shape.sources().get(level).schema().name());
        AccessPlanner.Access access = planner.plan(table, shape.sources().get(level).name(),
                level, available(select, level), frame);
        note(select, level, (outer == null ? "" : "  ") + access.description());

        for (long rowId : access.rowIds()) {
            current[level] = table.row(rowId);
            if (level > 0) {
                Join join = select.joins().get(level - 1);
                if (!Evaluator.isTrue(evaluator.eval(join.on(), frame))) {
                    continue;
                }
            }
            if (level < current.length - 1) {
                nest(select, shape, level + 1, current, outer, result);
            } else if (select.where() == null
                    || Evaluator.isTrue(evaluator.eval(select.where(), frame))) {
                result.add(current.clone());
            }
        }
        current[level] = null;
    }

    /** Las condiciones que pueden filtrar la tabla {@code level}: el WHERE y los ON hasta ella. */
    private static List<Expr> available(Select select, int level) {
        List<Expr> conditions = new ArrayList<>(AccessPlanner.conjuncts(select.where()));
        for (int j = 0; j < level; j++) {
            conditions.addAll(AccessPlanner.conjuncts(select.joins().get(j).on()));
        }
        return conditions;
    }

    private void note(Select select, int key, String line) {
        if (noted.computeIfAbsent(select, s -> new HashSet<>()).add(key)) {
            plan.add(line);
        }
    }

    private Object[] project(QueryShape shape, Frame frame) {
        Object[] row = new Object[shape.outputs().size()];
        for (int i = 0; i < row.length; i++) {
            row[i] = evaluator.eval(shape.outputs().get(i).expr(), frame);
        }
        return row;
    }

    /** Las claves de ORDER BY: una columna del resultado (por alias) o una expresion. */
    private Object[] sortKeys(Select select, QueryShape shape, Object[] row, Frame frame) {
        Object[] keys = new Object[select.orderBy().size()];
        for (int i = 0; i < keys.length; i++) {
            int target = shape.orderTargets().get(i);
            keys[i] = target >= 0 ? row[target] : evaluator.eval(select.orderBy().get(i).expr(), frame);
        }
        return keys;
    }

    /**
     * Ordena de forma estable: sin ORDER BY, o entre empates, se conserva el
     * orden de lectura. NULL va al final en ASC y al principio en DESC.
     */
    private List<Object[]> sort(Select select, List<Object[]> rows, List<Object[]> keys) {
        if (select.orderBy().isEmpty()) {
            return rows;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            order.add(i);
        }
        Comparator<Integer> comparator = (a, b) -> {
            for (int k = 0; k < select.orderBy().size(); k++) {
                OrderItem item = select.orderBy().get(k);
                int c = compareNullsLast(keys.get(a)[k], keys.get(b)[k]);
                if (c != 0) {
                    return item.descending() ? -c : c;
                }
            }
            return 0;
        };
        order.sort(comparator);
        List<Object[]> sorted = new ArrayList<>();
        order.forEach(i -> sorted.add(rows.get(i)));
        return sorted;
    }

    private static int compareNullsLast(Object a, Object b) {
        if (a == null || b == null) {
            return a == b ? 0 : (a == null ? 1 : -1);
        }
        return Values.compare(a, b);
    }

    // ---------------------------------------------------------- UPDATE, DELETE

    /**
     * Los registros de una tabla que cumplen el WHERE de un UPDATE o DELETE,
     * leidos antes de modificar nada.
     */
    private Map<Long, Object[]> matching(Table table, Expr where) {
        Map<Long, Object[]> result = new LinkedHashMap<>();
        AccessPlanner.Access access = planner.plan(table, table.schema().name(), 0,
                AccessPlanner.conjuncts(where), new Frame(null, new Object[1][]));
        plan.add(access.description());
        for (long rowId : access.rowIds()) {
            Object[] row = table.row(rowId);
            Frame frame = new Frame(null, new Object[][]{row});
            if (where == null || Evaluator.isTrue(evaluator.eval(where, frame))) {
                result.put(rowId, row);
            }
        }
        return result;
    }

    private StatementResult update(Update update) {
        Table table = db.table(update.table().name());
        TableSchema schema = table.schema();
        Map<Long, Object[]> targets = matching(table, update.where());

        // Los valores nuevos se calculan con el registro anterior: en
        // SET a = b, b = a los dos lados ven los valores originales.
        Map<Long, Object[]> changes = new LinkedHashMap<>();
        for (Map.Entry<Long, Object[]> entry : targets.entrySet()) {
            Frame frame = new Frame(null, new Object[][]{entry.getValue()});
            Object[] row = entry.getValue().clone();
            for (Assignment assignment : update.assignments()) {
                int column = schema.indexOf(assignment.column().name());
                row[column] = fit(schema.column(column),
                        evaluator.eval(assignment.value(), frame), assignment.value().pos());
            }
            changes.put(entry.getKey(), row);
        }

        List<Object[]> kept = new ArrayList<>();
        for (long rowId : table.rowIds()) {
            if (!changes.containsKey(rowId)) {
                kept.add(table.row(rowId));
            }
        }
        checkPrimaryKey(table, kept, new ArrayList<>(changes.values()), update.pos());
        changes.forEach((rowId, row) -> db.update(table, rowId, row));
        db.save(table);
        return new StatementResult(changes.size() + " fila(s) actualizada(s) en " + schema.name(),
                List.of(), List.of(), plan);
    }

    private StatementResult delete(Delete delete) {
        Table table = db.table(delete.table().name());
        Map<Long, Object[]> targets = matching(table, delete.where());
        targets.keySet().forEach(rowId -> db.delete(table, rowId));
        db.save(table);
        return new StatementResult(targets.size() + " fila(s) eliminada(s) de "
                + table.schema().name(), List.of(), List.of(), plan);
    }

    // ------------------------------------------------------------------ INSERT

    private StatementResult insert(Insert insert) {
        Table table = db.table(insert.table().name());
        TableSchema schema = table.schema();
        List<Integer> targets = new ArrayList<>();
        if (insert.columns().isEmpty()) {
            for (int i = 0; i < schema.columns().size(); i++) {
                targets.add(i);
            }
        } else {
            insert.columns().forEach(column -> targets.add(schema.indexOf(column.name())));
        }

        List<Object[]> newRows = new ArrayList<>();
        for (List<Expr> values : insert.rows()) {
            Object[] row = new Object[schema.columns().size()];
            for (int i = 0; i < values.size(); i++) {
                int column = targets.get(i);
                row[column] = fit(schema.column(column),
                        evaluator.eval(values.get(i), Frame.EMPTY), values.get(i).pos());
            }
            // Las columnas que el INSERT no menciona quedan en NULL; el analisis
            // ya verifico que todas ellas lo admiten.
            newRows.add(row);
        }

        checkPrimaryKey(table, table.rows(), newRows, insert.pos());
        newRows.forEach(row -> db.insert(table, row));
        db.save(table);
        return StatementResult.message(newRows.size() + " fila(s) insertada(s) en " + schema.name());
    }

    // -------------------------------------------------------------- restricciones

    /**
     * Ajusta un valor al tipo de la columna y verifica lo que el analisis no
     * pudo saber antes de ejecutar: un NULL calculado en una columna NOT NULL o
     * un texto calculado mas largo que el VARCHAR.
     */
    private static Object fit(Column column, Object value, Pos pos) {
        Object stored = Values.coerce(value, column.type());
        if (stored == null && !column.acceptsNull()) {
            throw new ExecutionException(pos, "la columna '" + column.name() + "' no admite NULL");
        }
        if (column.type().kind() == Kind.VARCHAR && stored instanceof String text
                && text.length() > column.type().length()) {
            throw new ExecutionException(pos, "el texto de " + text.length()
                    + " caracteres no cabe en la columna '" + column.name() + "' " + column.type());
        }
        return stored;
    }

    /**
     * Que la llave primaria siga siendo unica con los registros nuevos.
     *
     * @param kept    los registros que se quedan como estan
     * @param changed los registros nuevos o modificados
     */
    private static void checkPrimaryKey(Table table, Iterable<Object[]> kept,
                                        List<Object[]> changed, Pos pos) {
        int key = table.schema().primaryKeyIndex();
        if (key < 0) {
            return;
        }
        Set<Object> seen = new TreeSet<>(Values::compare);
        kept.forEach(row -> seen.add(row[key]));
        for (Object[] row : changed) {
            if (!seen.add(row[key])) {
                throw new ExecutionException(pos, "llave primaria duplicada: "
                        + table.schema().column(key).name() + " = " + Values.display(row[key])
                        + " ya existe en " + table.schema().name());
            }
        }
    }
}
