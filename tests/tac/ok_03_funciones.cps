// @esperado: TAC
// @genera: I11, I12, I13, I18
// Parametros, retorno, recursion y una funcion anidada que modifica una
// variable de su entorno de definicion por el enlace de acceso.

function factorial(n: integer): integer {
  if (n <= 1) {
    return 1;
  }
  return n * factorial(n - 1);
}

function sumar(a: integer, b: integer): integer {
  return a + b;
}

function crearContador(inicio: integer): integer {
  let cuenta: integer = inicio;

  function avanzar(paso: integer): integer {
    cuenta = cuenta + paso;
    return cuenta;
  }

  avanzar(1);
  return avanzar(2);
}

function anunciar(mensaje: string) {
  print(mensaje);
}

print(factorial(5));
print(sumar(factorial(3), 4));
print(crearContador(10));
anunciar("listo");
