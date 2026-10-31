package com.uvg.sql.engine;

import java.util.List;

/**
 * Los registros actuales de una consulta: uno por tabla del FROM/JOIN, en el
 * mismo orden que {@code QueryShape.sources()}. {@code parent} es el marco de
 * la consulta que contiene a esta; una columna con {@code depth = 1} se lee
 * de ahi (subconsulta correlacionada).
 *
 * <p>En una consulta agrupada, {@code group} son las combinaciones de
 * registros del grupo: sobre ellas se calculan COUNT, SUM, etc. {@code rows}
 * es entonces el primer registro del grupo, del que salen las columnas de
 * GROUP BY (iguales en todo el grupo).
 */
record Frame(Frame parent, Object[][] rows, List<Object[][]> group) {

    static final Frame EMPTY = new Frame(null, new Object[0][]);

    Frame(Frame parent, Object[][] rows) {
        this(parent, rows, null);
    }

    Frame up(int depth) {
        Frame frame = this;
        for (int i = 0; i < depth; i++) {
            frame = frame.parent;
        }
        return frame;
    }
}
