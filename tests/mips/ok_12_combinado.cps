// @esperado: MIPS
// @mips: M07, M08, M16, M18
// Combinaciones que estresan la convencion de llamada y getReg: temporales
// vivos a traves de llamadas y de etiquetas, un metodo con argumentos en la
// pila, llamadas de mas de cuatro argumentos anidadas y try en recursion.

class Par {
  let a: integer;
  let b: float;
  function constructor(a: integer, b: float) {
    this.a = a;
    this.b = b;
  }
  function cinco(p: integer, q: integer, r: float, s: integer): float {
    return this.b + p + q + r + s + this.a;
  }
}

function f(x: integer): integer {
  return x + 1;
}

function g(x: integer, y: integer, z: integer, w: integer, v: integer): integer {
  return x * 10000 + y * 1000 + z * 100 + w * 10 + v;
}

let p: Par = new Par(3, 0.5);
let lista: integer[] = [5, 6, 7];

// temporal vivo a traves de una llamada: lista[0] + f(...)
print(lista[0] + f(lista[1]) * f(2));

// metodo con 5 argumentos (this + 4): el quinto por la pila
print(p.cinco(1, 2, 0.25, 4));

// llamada anidada con >4 argumentos dentro de otra con >4 argumentos
print(g(1, g(0, 0, 0, 0, 2), 3, f(3), 5));

// ternario usado dentro de una expresion con temporales vivos
let k: integer = 4;
print(lista[2] * (k > 3 ? f(k) : 0) + lista[1]);

// booleano con cortocircuito como argumento
function dos(c: boolean, n: integer): integer {
  if (c) {
    return n;
  }
  return -n;
}
print(dos(k > 3 && lista[0] == 5, 9) + dos(k < 3 || lista[2] != 7, 1));

// foreach con objetos y llamadas en el cuerpo
let pares: Par[] = [new Par(1, 1.0), new Par(2, 2.0)];
let acumulado: float = 0.0;
foreach (x in pares) {
  acumulado = acumulado + x.cinco(0, 0, 0.0, 0);
}
print(acumulado);

// try dentro de recursion
function intenta(n: integer): integer {
  let r: integer = 0;
  try {
    if (n == 0) {
      r = lista[n + 5];
    } else {
      r = intenta(n - 1) + 1;
    }
  } catch (e) {
    r = 100;
  }
  return r;
}
print(intenta(3));

// while con llamada en la condicion
let c: integer = 0;
while (f(c) < 5) {
  c = c + 1;
}
print(c);

// floats con derrame
let h: float = 1.5;
print(h * 1 + (h * 2 + (h * 3 + (h * 4 + (h * 5 + (h * 6 + (h * 7 + (h * 8 + (h * 9 + h * 10)))))))));

// asignacion como expresion y compuestos
let m: integer = 0;
let n: integer = 0;
m = n = 7;
print(m + n);

// string en switch dentro de funcion con for
function clasifica(s: string): integer {
  let total: integer = 0;
  for (let i: integer = 0; i < 3; i = i + 1) {
    switch (s) {
      case "a":
        total = total + 1;
      case "b":
        total = total + 10;
        break;
      default:
        total = total + 100;
    }
  }
  return total;
}
print(clasifica("a"));
print(clasifica("b"));
print(clasifica("z"));
