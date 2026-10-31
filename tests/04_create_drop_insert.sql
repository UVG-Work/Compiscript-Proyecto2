-- Entrega 4: CREATE, DROP e INSERT, y que todo sobreviva a reabrir la base.
CREATE TABLE estudiantes (id INT PRIMARY KEY, nombre VARCHAR(20) NOT NULL, edad INT,
                          promedio FLOAT, activo BOOLEAN, nacimiento DATE);
INSERT INTO estudiantes VALUES (1, 'Ana', 20, 85, TRUE, '2004-03-15'),
                               (2, 'O''Brien, "Bob"', 17, 72.5, FALSE, DATE '2007-11-02');
-- las columnas omitidas quedan en NULL; la cadena vacia no es NULL
INSERT INTO estudiantes (id, nombre) VALUES (3, '');
INSERT INTO estudiantes (nombre, id, edad) VALUES ('Luis', 4, 2 * 9 + 1);
CREATE TABLE temporal (x INT);
DROP TABLE temporal;
-- @reabrir
SELECT * FROM estudiantes;
SELECT nombre FROM estudiantes WHERE nombre = '' OR activo IS NULL;
