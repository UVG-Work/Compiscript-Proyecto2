// @esperado: ERRORES 4
// @regla: L2, T6
// Regresion: el indice constante negativo no se detectaba.
//
// literalInteger devolvia -1 tanto para "no es constante" como para el indice
// -1, asi que lista[-1] se confundia con "no se puede saber" y pasaba limpio.
// Ahora devuelve null para lo primero, y el limite inferior se comprueba aunque
// no se conozca la longitud de la lista.

let lista: integer[] = [1, 2, 3];

print(lista[-1]);               // indice negativo, en lectura
lista[-2] = 5;                  // indice negativo, en escritura

let copia: integer[] = lista;   // longitud desconocida: solo aplica el minimo
print(copia[-1]);

lista.length = 10;              // 'length' es de solo lectura

print(lista[0]);
print(lista.length);
