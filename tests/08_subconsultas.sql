-- Entrega 7b: subconsultas con IN, NOT IN, EXISTS, NOT EXISTS y escalares.
CREATE TABLE estudiantes (id INT PRIMARY KEY, nombre VARCHAR(20) NOT NULL, edad INT);
CREATE TABLE cursos (id INT PRIMARY KEY, nombre VARCHAR(30) NOT NULL, creditos INT);
CREATE TABLE inscripciones (estudiante_id INT, curso_id INT, nota FLOAT);
INSERT INTO estudiantes VALUES (1, 'Ana', 20), (2, 'Bruno', 17), (3, 'Carla', 18), (4, 'Diego', 25);
INSERT INTO cursos VALUES (1, 'Compiladores', 5), (2, 'Bases de datos', 4), (3, 'Redes', 3);
INSERT INTO inscripciones VALUES (1, 1, 90), (1, 3, 75.5), (2, 3, 60), (3, 1, 85), (3, 2, NULL), (3, 3, 70);
-- el ejemplo del enunciado
SELECT nombre FROM estudiantes WHERE id IN (SELECT estudiante_id FROM inscripciones WHERE curso_id = 3);
SELECT nombre FROM estudiantes WHERE id NOT IN (SELECT estudiante_id FROM inscripciones);
SELECT e.nombre FROM estudiantes e
  WHERE EXISTS (SELECT * FROM inscripciones i WHERE i.estudiante_id = e.id AND i.nota > 80);
SELECT nombre FROM cursos c
  WHERE NOT EXISTS (SELECT * FROM inscripciones WHERE curso_id = c.id AND nota IS NULL);
SELECT nombre, edad FROM estudiantes WHERE edad > (SELECT AVG(edad) FROM estudiantes);
SELECT nombre, (SELECT COUNT(*) FROM inscripciones WHERE estudiante_id = e.id) AS cursos
  FROM estudiantes e ORDER BY cursos DESC, nombre;
-- una subconsulta escalar sin registros vale NULL
SELECT nombre FROM estudiantes WHERE edad = (SELECT edad FROM estudiantes WHERE nombre = 'Nadie');
UPDATE estudiantes SET edad = (SELECT MAX(edad) FROM estudiantes) WHERE nombre = 'Bruno';
DELETE FROM inscripciones WHERE curso_id IN (SELECT id FROM cursos WHERE creditos < 4);
SELECT nombre, edad FROM estudiantes ORDER BY id;
SELECT COUNT(*) FROM inscripciones;
-- NOT IN con un NULL en la subconsulta no devuelve nada, como en SQL
SELECT nombre FROM estudiantes WHERE edad NOT IN (SELECT nota FROM inscripciones);
