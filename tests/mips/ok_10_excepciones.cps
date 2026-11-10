// @esperado: MIPS
// @mips: M16, M14
// try/catch: errores dentro de llamadas profundas desenrollan la pila hasta
// el manejador, try anidados, break que cierra el try, y las variables
// modificadas antes del error llegan al catch con su valor.

let lista: integer[] = [1, 2, 3];
let menos: integer = -1;
let fuera: integer = 3;

function dividir(a: integer, b: integer): integer {
  return a / b;
}

function profundo(n: integer): integer {
  if (n == 0) {
    return lista[fuera + 7];
  }
  return profundo(n - 1) + 1;
}

try {
  print(dividir(10, 2));
  print(dividir(1, 0));
  print("no llega");
} catch (e) {
  print("atrapado: " + e);
}

try {
  print(profundo(5));
} catch (e) {
  print("desde lo profundo: " + e);
}

try {
  try {
    print(lista[menos]);
  } catch (interno) {
    print("interno: " + interno);
  }
  print(lista[fuera]);
} catch (externo) {
  print("externo: " + externo);
}

let i: integer = 0;
while (i < 5) {
  try {
    if (i == 2) {
      break;
    }
  } catch (e) {
    print("no deberia");
  }
  i = i + 1;
}
print(i);
try {
  print(lista[fuera * 2]);
} catch (e) {
  print("tras el break: " + e);
}

function seguro(k: integer): integer {
  let acumulado: integer = k;
  let cero: integer = 0;
  try {
    acumulado = acumulado + 5;
    acumulado = acumulado / cero;
  } catch (e) {
    acumulado = acumulado * 2;
  }
  return acumulado;
}
print(seguro(1));

class Nodo {
  let valor: integer = 1;
}
let nodo: Nodo = null;
try {
  print(nodo.valor);
} catch (e) {
  print(e);
}
print("fin");
