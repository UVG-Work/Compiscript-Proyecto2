// @esperado: MIPS
// @mips: M07, M08, M09
// Secuencia de llamada: hasta cuatro argumentos en $a0-$a3 y el resto por la
// pila, recursion simple y mutua, return dentro de un bucle, funcion sin
// valor y llamadas anidadas en los argumentos.

function suma6(a: integer, b: integer, c: integer, d: integer, e: integer, f: integer): integer {
  return a + 2 * b + 3 * c + 4 * d + 5 * e + 6 * f;
}
print(suma6(1, 2, 3, 4, 5, 6));

function mezcla(a: float, b: integer, c: string, d: boolean, e: float): string {
  if (d) {
    return c + "!";
  }
  return c;
}
print(mezcla(1.5, 2, "hey", true, 2.5));

function fib(n: integer): integer {
  if (n < 2) {
    return n;
  }
  return fib(n - 1) + fib(n - 2);
}
print(fib(15));

function esPar(n: integer): boolean {
  if (n == 0) {
    return true;
  }
  return esImpar(n - 1);
}

function esImpar(n: integer): boolean {
  if (n == 0) {
    return false;
  }
  return esPar(n - 1);
}
print(esPar(10));
print(esImpar(7));

function raiz(objetivo: integer): integer {
  for (let i: integer = 0; i < 100; i = i + 1) {
    if (i * i >= objetivo) {
      return i;
    }
  }
  return -1;
}
print(raiz(50));

function saluda(nombre: string) {
  print("hola " + nombre);
}
saluda("mundo");

print(suma6(fib(1), fib(2), fib(3), fib(4), fib(5), fib(6)));
