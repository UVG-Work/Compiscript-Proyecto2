// @esperado: TAC
// @genera: I16, I20
// Creacion de listas, lectura y escritura por indice, length, listas de
// listas y foreach.

let numeros: integer[] = [4, 8, 15];
let vacia: integer[] = [];
let matriz: integer[][] = [[1, 2], [3, 4]];
let nombres: string[] = ["ana", "beto"];

numeros[1] = numeros[0] + numeros[2];
matriz[1][0] = 9;

let total: integer = 0;
foreach (n in numeros) {
  total = total + n;
}
for (let i: integer = 0; i < numeros.length; i = i + 1) {
  total = total + numeros[i];
}

print(total);
print(matriz[1][0]);
print(vacia.length);
print(nombres[1]);
