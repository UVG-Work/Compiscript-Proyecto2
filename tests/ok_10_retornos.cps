// @esperado: OK
// @regla: F2, C1, C2
// Caminos de retorno que si terminan, y que el analisis conservador marcaba
// como "hay caminos que no retornan".

// Un bucle infinito sin break no cae nunca por debajo.
function primero(): integer {
  while (true) {
    return 1;
  }
}

// El cuerpo de un do-while corre al menos una vez.
function segundo(): integer {
  do {
    return 2;
  } while (true);
}

// switch con default y todas las ramas retornando.
function tercero(n: integer): string {
  switch (n) {
    case 1: { return "uno"; }
    case 2: { return "dos"; }
    default: { return "otro"; }
  }
}

// try y catch retornan los dos.
function cuarto(): integer {
  try {
    return 4;
  } catch (e) {
    print(e);
    return 0;
  }
}

// Y el caso de siempre: if/else donde retornan las dos ramas.
function quinto(n: integer): integer {
  if (n > 0) {
    return n;
  } else {
    return 0 - n;
  }
}

print(primero() + segundo() + cuarto() + quinto(0 - 5));
print(tercero(1));
