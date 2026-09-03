// @esperado: ERRORES 8
// @regla: C1, C2, G1, T3, L1
// Control de flujo. Los terminadores van aislados en su propio bloque: si se
// escriben seguidos, cada uno marca al siguiente como codigo muerto y la
// prueba termina midiendo otra regla.

let x: integer = 1;

{ break; }                      // break fuera de un bucle
{ continue; }                   // continue fuera de un bucle

while (x) {                     // condicion que no es boolean
  x = x - 1;
}

do {
  x = x + 1;
} while (x);                    // condicion que no es boolean

for (let i: integer = 0; i; i = i + 1) {   // condicion que no es boolean
  print(i);
}

function conMuerto(): integer {
  return 1;
  print("inalcanzable");        // codigo muerto
}

foreach (n in x) {              // foreach sobre algo que no es lista
  print(n);
}

switch (x) {                    // el selector no se exige boolean: ver ok_09
  case "texto":                 // el case no es comparable con el selector
    print("nunca");
}

print(conMuerto());
