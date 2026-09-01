// @esperado: ERRORES 3
// @regla: L2
// Regresion: un literal de indice que no cabe en 32 bits reventaba el
// analizador con NumberFormatException. No era un error mal reportado, era el
// proceso entero cayendose: tumbaba el CLI, el IDE y abortaba esta bateria.

let lista: integer[] = [1, 2, 3];

print(lista[99999999999999999999]);   // no cabe ni en un long
print(lista[2147483648]);             // justo por encima del maximo de int
print(lista[3]);                      // fuera de rango por uno

print(lista[2]);
print(lista[0]);
