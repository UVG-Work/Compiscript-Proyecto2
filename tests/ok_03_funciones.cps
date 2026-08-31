// @esperado: OK
// @regla: F1, F2, F3, F4, F5, C3, G1, A5
// Funciones: recursion, recursion mutua y closures.
// La recursion mutua es la prueba de que el adelanto de declaraciones funciona:
// par llama a impar, que se declara mas abajo.

function factorial(n: integer): integer {
  if (n <= 1) {
    return 1;
  }
  return n * factorial(n - 1);
}

function par(n: integer): boolean {
  if (n == 0) {
    return true;
  }
  return impar(n - 1);
}

function impar(n: integer): boolean {
  if (n == 0) {
    return false;
  }
  return par(n - 1);
}

function crearContador(): integer {
  let cuenta: integer = 0;

  function siguiente(): integer {
    cuenta = cuenta + 1;
    return cuenta;
  }

  return siguiente();
}

function acumula(n: integer): integer {
  n = n + 1;
  return n;
}

function saludar(nombre: string): string {
  return "Hola " + nombre;
}

function repetible(): string {
  // El mismo nombre en otro ambito no es redeclaracion: sombrea, no choca.
  function saludar(): string {
    return "saludo interno";
  }
  return saludar();
}

function anunciar(mensaje: string) {
  print(mensaje);
  return;
}

print(factorial(5));
print(par(4));
print(impar(3));
print(crearContador());
print(acumula(2));
print(saludar("mundo"));
print(repetible());
anunciar("listo");
