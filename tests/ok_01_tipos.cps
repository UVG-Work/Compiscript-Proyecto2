// @esperado: OK
// @regla: T1, T2, T3, T4, T5, C1, G2
// Tipos: aritmetica, logica, comparaciones, asignaciones, const e inferencia.

let a: integer = 10;
let b: integer = 3;
let suma: integer = a + b;
let resto: integer = a % b;
let negativo: integer = -a;

let saludo: string = "hola";
let frase: string = saludo + " mundo";

let mayor: boolean = a > b;
let iguales: boolean = a == b;
let ambos: boolean = mayor && !iguales;

const LIMITE: integer = 100;
let cabe: boolean = suma < LIMITE;

let inferido = 5;
let tambien = "texto";
let nulo = null;
let elegido: integer = mayor ? a : b;

let sinValor: string;
sinValor = "asignada despues";

if (cabe) {
  print(suma);
}

print(resto);
print(negativo);
print(frase);
print(ambos);
print(inferido);
print(tambien);
print(nulo);
print(elegido);
print(sinValor);
