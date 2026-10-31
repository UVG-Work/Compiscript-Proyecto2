-- Entrega 2: el AST. Palabras clave sin distinguir mayusculas, identificadores
-- en minusculas, NOT EXISTS como un solo nodo y -1 como literal.
-- @ast
create table Estudiantes (ID int primary key, Nombre varchar(20) not null);
CREATE TABLE cursos (id INT, cupo INT);
CREATE TABLE inscripciones (estudiante_id INT, curso_id INT);
SELECT e.Nombre, COUNT(*) AS total
FROM estudiantes e INNER JOIN inscripciones i ON e.id = i.estudiante_id
WHERE e.id IN (SELECT estudiante_id FROM inscripciones WHERE curso_id = 3)
  AND NOT EXISTS (SELECT * FROM cursos c WHERE c.id = i.curso_id AND c.cupo > -1)
GROUP BY e.nombre HAVING COUNT(*) > 1 ORDER BY total DESC;
