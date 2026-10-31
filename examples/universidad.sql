-- Base de ejemplo del enunciado: estudiantes, cursos e inscripciones.
-- Uso: java -jar sql-interprete.jar examples/universidad.sql --db database
-- (sobre una carpeta vacia; una segunda corrida falla porque las tablas ya existen)

CREATE TABLE estudiantes (
  id         INT PRIMARY KEY,
  nombre     VARCHAR(40) NOT NULL,
  edad       INT,
  promedio   FLOAT,
  becado     BOOLEAN,
  ingreso    DATE
);

CREATE TABLE cursos (
  id        INT PRIMARY KEY,
  nombre    VARCHAR(40) NOT NULL,
  creditos  INT
);

CREATE TABLE inscripciones (
  estudiante_id INT NOT NULL,
  curso_id      INT NOT NULL,
  nota          FLOAT
);

INSERT INTO estudiantes VALUES
  (1, 'Ana Lopez',     20, 88.5, TRUE,  '2023-01-16'),
  (2, 'Bruno Perez',   17, 71.0, FALSE, '2024-01-15'),
  (3, 'Carla Mendez',  18, 92.3, TRUE,  '2024-01-15'),
  (4, 'Diego Ramirez', 25, 64.8, FALSE, '2021-07-12'),
  (5, 'Elena Castro',  19, NULL, NULL,  '2023-07-10');

INSERT INTO cursos VALUES
  (1, 'Compiladores', 5), (2, 'Bases de datos', 4), (3, 'Redes', 3), (4, 'Etica', 2);

INSERT INTO inscripciones VALUES
  (1, 1, 90), (1, 3, 75.5), (2, 3, 60), (3, 1, 85), (3, 2, NULL), (3, 3, 70), (5, 2, 95);

CREATE INDEX idx_edad ON estudiantes(edad);
CREATE INDEX idx_insc_estudiante ON inscripciones(estudiante_id);

-- El ejemplo del enunciado (usa idx_edad)
SELECT nombre FROM estudiantes WHERE edad >= 18;

-- La subconsulta del enunciado
SELECT nombre
FROM estudiantes
WHERE id IN (SELECT estudiante_id
             FROM inscripciones
             WHERE curso_id = 3);

-- JOIN, agregaciones, GROUP BY y HAVING
SELECT c.nombre AS curso, COUNT(*) AS inscritos, AVG(i.nota) AS promedio
FROM cursos c INNER JOIN inscripciones i ON c.id = i.curso_id
GROUP BY c.nombre
HAVING COUNT(*) >= 2
ORDER BY inscritos DESC, curso;

-- Cursos sin inscritos
SELECT nombre FROM cursos c
WHERE NOT EXISTS (SELECT * FROM inscripciones i WHERE i.curso_id = c.id);
