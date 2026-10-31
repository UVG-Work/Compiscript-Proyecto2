-- Entrega 6: INNER JOIN, agregaciones, GROUP BY y HAVING.
CREATE TABLE estudiantes (id INT PRIMARY KEY, nombre VARCHAR(20) NOT NULL, edad INT);
CREATE TABLE cursos (id INT PRIMARY KEY, nombre VARCHAR(30) NOT NULL, creditos INT);
CREATE TABLE inscripciones (estudiante_id INT, curso_id INT, nota FLOAT);
INSERT INTO estudiantes VALUES (1, 'Ana', 20), (2, 'Bruno', 17), (3, 'Carla', 18), (4, 'Diego', 25);
INSERT INTO cursos VALUES (1, 'Compiladores', 5), (2, 'Bases de datos', 4), (3, 'Redes', 3);
INSERT INTO inscripciones VALUES (1, 1, 90), (1, 3, 75.5), (2, 3, 60), (3, 1, 85), (3, 2, NULL), (3, 3, 70);
SELECT e.nombre, c.nombre AS curso, i.nota
  FROM estudiantes e INNER JOIN inscripciones i ON e.id = i.estudiante_id
  JOIN cursos c ON c.id = i.curso_id
  WHERE c.creditos >= 4 ORDER BY e.nombre, curso;
SELECT c.nombre, COUNT(*) AS inscritos, COUNT(i.nota) AS con_nota, AVG(i.nota) AS promedio,
       MIN(i.nota), MAX(i.nota), SUM(c.creditos)
  FROM cursos c INNER JOIN inscripciones i ON c.id = i.curso_id
  GROUP BY c.nombre HAVING COUNT(*) >= 2 ORDER BY inscritos DESC, c.nombre;
SELECT COUNT(*), SUM(edad), AVG(edad), MIN(nombre), MAX(nombre) FROM estudiantes;
-- sobre una tabla vacia (o un WHERE sin resultados) COUNT da 0 y las demas NULL
SELECT COUNT(*), SUM(edad) FROM estudiantes WHERE edad > 100;
SELECT edad / 10 AS decada, COUNT(*) FROM estudiantes GROUP BY edad / 10 ORDER BY decada;
SELECT e.nombre, SUM(c.creditos) AS creditos FROM estudiantes e
  INNER JOIN inscripciones i ON e.id = i.estudiante_id INNER JOIN cursos c ON c.id = i.curso_id
  GROUP BY e.nombre ORDER BY SUM(c.creditos) DESC;
