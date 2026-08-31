// @esperado: OK
// @regla: L1, L2, T6, C1
// Listas: homogeneidad, indices, anidamiento, length y recorrido.

let numeros: integer[] = [1, 2, 3];
let palabras: string[] = ["uno", "dos"];
let matriz: integer[][] = [[1, 2], [3, 4]];
let vacia: integer[] = [];

let primero: integer = numeros[0];
let celda: integer = matriz[1][0];
let cuantos: integer = numeros.length;

numeros[1] = 20;

let suma: integer = 0;
foreach (n in numeros) {
  suma = suma + n;
}

for (let k: integer = 0; k < numeros.length; k = k + 1) {
  print(numeros[k]);
}

print(primero);
print(celda);
print(cuantos);
print(palabras[0]);
print(vacia);
print(suma);
