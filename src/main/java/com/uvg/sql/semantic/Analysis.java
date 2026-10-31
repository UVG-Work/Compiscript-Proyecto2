package com.uvg.sql.semantic;

import com.uvg.sql.ast.Expr;
import com.uvg.sql.ast.Expr.ColumnRef;
import com.uvg.sql.ast.Statement.Select;
import com.uvg.sql.catalog.TableSchema;
import com.uvg.sql.types.DataType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que el analisis semantico deja anotado sobre el AST para que el
 * interprete no vuelva a resolver nombres. Los mapas son por identidad (ver
 * {@link Expr}).
 */
public final class Analysis {

    /**
     * A que columna apunta una referencia. {@code depth} cuenta cuantas
     * consultas hay que subir: 0 es la consulta donde aparece y 1 la que la
     * contiene (una subconsulta correlacionada). {@code source} es la posicion
     * de la tabla en el FROM/JOIN de esa consulta y {@code column} la posicion
     * de la columna en su esquema.
     */
    public record ColumnBinding(int depth, int source, int column, DataType type) {
    }

    /** Una tabla del FROM o de un JOIN con el nombre que se le da en la consulta. */
    public record Source(String name, TableSchema schema) {
    }

    /** Una columna del resultado: su encabezado y la expresion que la calcula. */
    public record Output(String name, DataType type, Expr expr) {
    }

    /**
     * La forma de un SELECT ya analizado.
     *
     * @param grouped      si se evalua por grupos (GROUP BY, HAVING o agregaciones)
     * @param orderTargets por cada elemento de ORDER BY, la posicion de la
     *                     columna del resultado a la que se refiere por su
     *                     alias, o -1 si es una expresion a evaluar
     */
    public record QueryShape(List<Source> sources, List<Output> outputs, boolean grouped,
                             List<Integer> orderTargets) {
    }

    private final Map<ColumnRef, ColumnBinding> bindings = new IdentityHashMap<>();
    private final Map<Select, QueryShape> shapes = new IdentityHashMap<>();
    private final Set<Select> correlated = Collections.newSetFromMap(new IdentityHashMap<>());

    void bind(ColumnRef ref, ColumnBinding binding) {
        bindings.put(ref, binding);
    }

    void shape(Select select, QueryShape shape) {
        shapes.put(select, shape);
    }

    void markCorrelated(Select select) {
        correlated.add(select);
    }

    /**
     * Una subconsulta correlacionada usa columnas de la consulta que la
     * contiene, asi que hay que ejecutarla de nuevo por cada registro externo.
     * Las demas dan siempre lo mismo y se ejecutan una sola vez.
     */
    public boolean isCorrelated(Select select) {
        return correlated.contains(select);
    }

    public ColumnBinding binding(ColumnRef ref) {
        return bindings.get(ref);
    }

    public QueryShape shape(Select select) {
        return shapes.get(select);
    }
}
