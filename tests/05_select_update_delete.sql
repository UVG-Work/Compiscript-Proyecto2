-- Entrega 5: SELECT, WHERE, ORDER BY, UPDATE y DELETE.
CREATE TABLE estudiantes (id INT PRIMARY KEY, nombre VARCHAR(20) NOT NULL, edad INT, promedio FLOAT);
INSERT INTO estudiantes VALUES (1, 'Ana', 20, 85.5), (2, 'Bruno', 17, 72), (3, 'Carla', 18, NULL),
                               (4, 'Diego', NULL, 90), (5, 'Elena', 22, 60);
-- el ejemplo del enunciado
SELECT nombre FROM estudiantes WHERE edad >= 18;
-- AND, OR y NULL con logica de tres valores
SELECT nombre, edad, promedio * 2 AS doble FROM estudiantes
  WHERE edad > 17 AND (promedio > 80 OR promedio IS NULL) ORDER BY edad DESC;
-- NULL va al final en ASC
SELECT * FROM estudiantes ORDER BY promedio;
SELECT nombre FROM estudiantes WHERE edad IN (17, 22) OR NOT (edad <> 18);
SELECT nombre, edad / 2, edad % 2, -promedio FROM estudiantes WHERE id <= 2 ORDER BY id DESC;
-- SET usa los valores anteriores del registro
UPDATE estudiantes SET edad = edad + 1, promedio = 75 WHERE nombre = 'Bruno';
UPDATE estudiantes SET edad = id, id = edad WHERE id = 5;
DELETE FROM estudiantes WHERE edad IS NULL;
-- @reabrir
SELECT id, nombre, edad, promedio FROM estudiantes ORDER BY id;
DELETE FROM estudiantes;
SELECT COUNT(*) FROM estudiantes;
