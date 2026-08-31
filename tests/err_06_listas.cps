// @esperado: ERRORES 9
// @regla: L1, L2, T4, T6
// Listas: homogeneidad, indices y compatibilidad.

let numeros: integer[] = [1, 2, 3];
let mezclada = [1, "dos", true];        // la lista no es homogenea
let malIndice: integer = numeros["a"];  // el indice no es integer
let fuera: integer = numeros[5];        // indice fuera de rango

let noLista: integer = 7;
print(noLista[0]);                      // indexar algo que no es lista

numeros[0] = "texto";                   // elemento incompatible
let malTipo: string[] = numeros;        // integer[] no cabe en string[]
print(numeros.tamano);                  // las listas solo exponen length

let vacia = [];                         // no se puede inferir de una lista vacia

foreach (n in noLista) {                // foreach sobre algo que no es lista
  print(n);
}

print(mezclada);
print(malIndice);
print(fuera);
print(malTipo);
print(vacia);
