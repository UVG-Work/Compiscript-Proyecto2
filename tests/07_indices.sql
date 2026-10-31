-- Entrega 7a: indices. El plan muestra si se uso un indice o un recorrido
-- completo; el resultado debe ser el mismo en los dos casos.
CREATE TABLE estudiantes (id INT PRIMARY KEY, nombre VARCHAR(20) NOT NULL, edad INT, nacimiento DATE);
CREATE TABLE cursos (id INT PRIMARY KEY, nombre VARCHAR(30) NOT NULL);
CREATE TABLE inscripciones (estudiante_id INT, curso_id INT);
INSERT INTO estudiantes VALUES (1, 'Ana', 20, '2004-03-15'), (2, 'Bruno', 17, '2007-11-02'),
                               (3, 'Carla', 18, '2006-01-20'), (4, 'Diego', 25, NULL);
INSERT INTO cursos VALUES (1, 'Compiladores'), (2, 'Bases de datos'), (3, 'Redes');
INSERT INTO inscripciones VALUES (1, 1), (1, 3), (2, 3), (3, 1), (3, 2), (3, 3);
SELECT nombre FROM estudiantes WHERE edad >= 18 ORDER BY nombre;
CREATE INDEX idx_edad ON estudiantes(edad);
CREATE INDEX idx_nacimiento ON estudiantes(nacimiento);
CREATE INDEX idx_cursos_id ON cursos(id);
CREATE INDEX idx_insc_est ON inscripciones(estudiante_id);
SELECT nombre FROM estudiantes WHERE edad >= 18 ORDER BY nombre;
-- la constante a la izquierda tambien sirve
SELECT nombre FROM estudiantes WHERE 20 > edad;
SELECT nombre FROM estudiantes WHERE edad IN (17, 25) AND nombre <> 'Diego';
SELECT nombre FROM estudiantes WHERE nacimiento < '2006-06-01';
-- en un JOIN, cada tabla interna busca en su indice con el valor externo
SELECT e.nombre, c.nombre FROM estudiantes e INNER JOIN inscripciones i ON e.id = i.estudiante_id
  INNER JOIN cursos c ON c.id = i.curso_id WHERE e.edad = 18;
-- los indices se mantienen al insertar, actualizar y borrar
INSERT INTO estudiantes VALUES (5, 'Eva', 18, '2006-05-05');
UPDATE estudiantes SET edad = 30 WHERE edad = 25;
DELETE FROM estudiantes WHERE edad < 18;
-- @reabrir
SELECT nombre, edad FROM estudiantes WHERE edad = 18;
SELECT nombre, edad FROM estudiantes WHERE edad > 25;
DROP INDEX idx_edad;
SELECT nombre, edad FROM estudiantes WHERE edad = 18;
