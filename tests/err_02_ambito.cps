// @esperado: ERRORES 6
// @regla: A1, A2, A3, A4, G3
// Reglas de ambito y resolucion de nombres.

let x: integer = 1;
let x: string = "repetida";     // redeclaracion en el mismo ambito

print(desconocida);             // nombre no declarado
noExiste = 5;                   // asignacion a un nombre no declarado

function f(): integer {
  let local: integer = 2;
  return local;
}

print(local);                   // local no es visible fuera de la funcion

{
  let dentro: integer = 3;
  print(dentro);
}
print(dentro);                  // dentro no es visible fuera del bloque

let sinValor: integer;
print(sinValor);                // uso antes de asignarle un valor

print(x);
print(f());
