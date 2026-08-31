// @esperado: ERRORES 4
// @regla: G1, G2
// Reglas generales: codigo muerto y uso antes de asignar.
// Un tramo inalcanzable produce un solo mensaje, no uno por sentencia.

function conMuerto(): integer {
  return 1;
  print("inalcanzable");
  print("tambien inalcanzable");
}

function bucle(): integer {
  let i: integer = 0;
  while (i < 3) {
    i = i + 1;
    break;
    print("nunca");
  }
  return i;
}

let sinAsignar: integer;
print(sinAsignar);

let absurdo = conMuerto * bucle;   // multiplicar funciones no tiene sentido
print(absurdo);

print(conMuerto());
print(bucle());
