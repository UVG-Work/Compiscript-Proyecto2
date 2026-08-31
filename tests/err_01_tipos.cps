// @esperado: ERRORES 8
// @regla: T1, T2, T3, T4, T5, C1
// Cada linea rompe una regla de tipos distinta.

let a: integer = 10;
let texto: string = "hola";

let malo: integer = texto;            // asignacion incompatible
let suma: integer = a + texto;        // aritmetica entre integer y string
let logico: boolean = a && true;      // operador logico sobre integer
let comparado: boolean = texto < a;   // relacional entre string e integer
let negado: boolean = !a;             // negacion de un integer

const FIJO: integer = 1;
FIJO = 2;                             // reasignar una constante

let sinTipo;                          // sin anotacion y sin valor inicial

if (a) {                              // condicion que no es boolean
  print(a);
}

print(malo);
print(suma);
print(logico);
print(comparado);
print(negado);
