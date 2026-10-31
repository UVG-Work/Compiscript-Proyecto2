-- Errores que solo aparecen al ejecutar. Cada uno detiene el script y la
-- sentencia que fallo no deja cambios a medias.
CREATE TABLE t (id INT PRIMARY KEY, nombre VARCHAR(5), n INT);
INSERT INTO t VALUES (1, 'a', 10), (2, 'b', 0);
-- llave primaria duplicada dentro del mismo INSERT: no se inserta ninguna fila
INSERT INTO t VALUES (3, 'c', 1), (1, 'd', 2);
-- @reabrir
SELECT * FROM t;
-- subconsulta escalar con mas de un registro
SELECT nombre FROM t WHERE n = (SELECT n FROM t);
-- @reabrir
UPDATE t SET id = 1 WHERE id = 2;
-- @reabrir
SELECT n / (SELECT MIN(n) FROM t) FROM t;
-- @reabrir
SELECT * FROM t;
