// @esperado: ERRORES 2
// @regla: L1, A2
// Regresion: un error real debe producir un mensaje, no dos.
//
// ArrayType(ERROR) no es ErrorType, asi que el sumidero no lo reconocia y cada
// arreglo con elemento erroneo soltaba un segundo mensaje derivado del primero.

let mezclada: integer[] = [1, [2]];      // la lista no es homogenea
let ajena: Desconocido[] = [1, 2];       // el tipo no esta declarado

// Esta linea no debe agregar nada: la causa ya se reporto arriba.
let suma: integer = mezclada[0] + ajena[0];

print(mezclada);
print(ajena);
print(suma);
