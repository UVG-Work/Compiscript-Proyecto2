// @esperado: ERRORES 4
// @regla: T1, T2, T4
// float: lo que no se permite.

let x: integer = 1.5;               // no hay estrechamiento float -> integer

let y: float = 2.5;
let resto: integer = y % 2;         // el operador % solo acepta integer
let texto: string = "n" + 1.5;      // no hay concatenacion entre string y numero
let logico: boolean = 1.5 && true;  // los operadores logicos solo aceptan boolean

print(x);
print(resto);
print(texto);
print(logico);
