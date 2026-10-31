-- Entrega 3: un error por cada caso de la lista del enunciado. El analisis
-- revisa el script completo antes de ejecutar, asi que nada de esto corre.
CREATE TABLE estudiantes (id INT PRIMARY KEY, nombre VARCHAR(5) NOT NULL, edad INT, nacimiento DATE);
CREATE TABLE cursos (id INT PRIMARY KEY, nombre VARCHAR(30));
CREATE TABLE inscripciones (estudiante_id INT, curso_id INT);
CREATE INDEX idx_edad ON estudiantes(edad);
-- tabla inexistente
SELECT * FROM alumnos;
-- columna inexistente
SELECT apellido FROM estudiantes;
-- tipos incompatibles
SELECT nombre FROM estudiantes WHERE edad = 'veinte';
SELECT nombre FROM estudiantes WHERE nombre + 1 > 2;
-- valores incorrectos en INSERT: tipo, longitud, fecha, NOT NULL, cantidad
INSERT INTO estudiantes VALUES (1, 'Ana', 'x', '2024-01-01'), (2, 'Bartolomeo', 20, '2024-13-01');
INSERT INTO estudiantes (id, edad) VALUES (3, 21);
INSERT INTO estudiantes VALUES (4, 'Luis');
INSERT INTO estudiantes VALUES (NULL, 'Eva', 20, NULL);
-- referencias ambiguas o invalidas
SELECT nombre FROM estudiantes e INNER JOIN cursos c ON e.id = c.id;
SELECT x.nombre FROM estudiantes e;
SELECT estudiantes.nombre FROM estudiantes e;
-- indice duplicado o inexistente
CREATE INDEX idx_edad ON cursos(id);
CREATE INDEX idx_otro ON estudiantes(edad);
DROP INDEX idx_nada;
-- subconsulta escalar o de IN con mas de una columna
SELECT nombre FROM estudiantes WHERE edad > (SELECT id, nombre FROM cursos);
SELECT nombre FROM estudiantes WHERE id IN (SELECT * FROM cursos);
-- GROUP BY y agregaciones mal usadas
SELECT nombre, COUNT(*) FROM estudiantes GROUP BY edad;
SELECT nombre FROM estudiantes WHERE COUNT(*) > 1;
SELECT SUM(nombre) FROM estudiantes;
-- la condicion debe ser BOOLEAN
SELECT nombre FROM estudiantes WHERE edad;
-- ORDER BY por posicion no esta soportado y se rechaza en vez de ignorarse
SELECT nombre, edad FROM estudiantes ORDER BY 2;
-- un DROP TABLE del mismo script se toma en cuenta
DROP TABLE inscripciones;
SELECT * FROM inscripciones;
-- estas son validas y no reportan nada
SELECT e.nombre, COUNT(*) AS total FROM estudiantes e INNER JOIN cursos c ON e.id = c.id
  WHERE e.id IN (SELECT id FROM cursos) AND e.nacimiento >= '2000-01-01'
  GROUP BY e.nombre HAVING COUNT(*) > 1 ORDER BY total DESC;
SELECT nombre FROM estudiantes WHERE edad = (SELECT MAX(edad) FROM estudiantes);
