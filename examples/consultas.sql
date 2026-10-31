-- Consultas sobre la base de examples/universidad.sql (ya creada y leida desde los archivos)
UPDATE estudiantes SET edad = edad + 1 WHERE edad = 17;
SELECT nombre, edad FROM estudiantes
WHERE edad > (SELECT AVG(edad) FROM estudiantes);
SELECT e.nombre, c.nombre AS curso, i.nota
FROM estudiantes e
INNER JOIN inscripciones i ON i.estudiante_id = e.id
INNER JOIN cursos c ON c.id = i.curso_id
WHERE e.edad = 18
ORDER BY i.nota DESC;
