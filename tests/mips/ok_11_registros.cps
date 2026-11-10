// @esperado: MIPS
// @mips: M18, M19
// Presion de registros. La expresion deja once temporales vivos a la vez y
// solo hay ocho $t: getReg derrama a la pila el que se va a usar mas tarde.
// La funcion usa doce variables en un mismo bloque y solo hay ocho $s.

let a: integer = 2;
let r: integer = a * 1 + (a * 2 + (a * 3 + (a * 4 + (a * 5 + (a * 6 + (a * 7 + (a * 8 + (a * 9 + (a * 10 + a * 11)))))))));
print(r);

function muchas(): integer {
  let v1: integer = 1;
  let v2: integer = 2;
  let v3: integer = 3;
  let v4: integer = 4;
  let v5: integer = 5;
  let v6: integer = 6;
  let v7: integer = 7;
  let v8: integer = 8;
  let v9: integer = 9;
  let v10: integer = 10;
  let v11: integer = 11;
  let v12: integer = 12;
  return v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8 + v9 + v10 + v11 + v12 + v1 * v12;
}
print(muchas());
print(muchas() + r);
