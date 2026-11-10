// @esperado: MIPS
// @mips: M08, M10, M12, M13, M16
// Lista enlazada y arbol binario con objetos, cadena de herencia de cuatro
// niveles, closures de tres niveles, ocho argumentos mezclados y try dentro
// de un for con continue.
class Nodo {
  let valor: integer;
  let siguiente: Nodo;
  function constructor(v: integer, s: Nodo) {
    this.valor = v;
    this.siguiente = s;
  }
}

let cabeza: Nodo = null;
for (let i: integer = 1; i <= 10; i = i + 1) {
  cabeza = new Nodo(i * i, cabeza);
}

function sumar(n: Nodo): integer {
  let total: integer = 0;
  while (n != null) {
    total = total + n.valor;
    n = n.siguiente;
  }
  return total;
}
print(sumar(cabeza));

function invertir(n: Nodo): Nodo {
  let previo: Nodo = null;
  while (n != null) {
    let sig: Nodo = n.siguiente;
    n.siguiente = previo;
    previo = n;
    n = sig;
  }
  return previo;
}
cabeza = invertir(cabeza);
print(cabeza.valor);
print(cabeza.siguiente.siguiente.valor);

class Arbol {
  let clave: integer;
  let izq: Arbol;
  let der: Arbol;
  function constructor(c: integer) {
    this.clave = c;
  }
  function insertar(c: integer) {
    if (c < clave) {
      if (izq == null) {
        izq = new Arbol(c);
      } else {
        izq.insertar(c);
      }
    } else {
      if (der == null) {
        der = new Arbol(c);
      } else {
        der.insertar(c);
      }
    }
  }
  function enOrden(): string {
    let s: string = "";
    if (izq != null) {
      s = izq.enOrden();
    }
    s = s + "(" + aTexto(clave) + ")";
    if (der != null) {
      s = s + der.enOrden();
    }
    return s;
  }
  function altura(): integer {
    let a: integer = izq == null ? 0 : izq.altura();
    let b: integer = der == null ? 0 : der.altura();
    return 1 + (a > b ? a : b);
  }
}

function aTexto(n: integer): string {
  let d: string[] = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];
  if (n < 10) {
    return d[n];
  }
  return aTexto(n / 10) + d[n % 10];
}

let raiz: Arbol = new Arbol(50);
let claves: integer[] = [30, 70, 20, 40, 60, 80, 35, 45, 65, 10, 5, 90, 85];
foreach (c in claves) {
  raiz.insertar(c);
}
print(raiz.enOrden());
print(raiz.altura());

class A {
  let n: integer = 1;
  function f(): integer { return n; }
  function g(): integer { return this.f() * 10; }
}
class B : A {
  function f(): integer { return this.n + 1; }
}
class C : B {
  let extra: integer = 5;
  function g(): integer { return this.f() * 100 + extra; }
}
class D : C {
  function f(): integer { return 7; }
}
let objs: A[] = [new A(), new B(), new C(), new D()];
let acumula: integer = 0;
foreach (o in objs) {
  print(o.g());
  acumula = acumula + o.f();
}
print(acumula);

function generador(inicio: integer): integer {
  let estado: integer = inicio;
  function paso(k: integer): integer {
    function interno(): integer {
      estado = estado * 2 + k;
      return estado;
    }
    let x: integer = interno();
    let y: integer = interno();
    return x + y;
  }
  let total: integer = 0;
  for (let i: integer = 1; i <= 4; i = i + 1) {
    total = total + paso(i);
  }
  return total + estado;
}
print(generador(1));

function ochoArgs(a: integer, b: float, c: integer, d: string, e: integer, f: float, g: boolean, h: integer): float {
  if (g) {
    return a + b + c + e + f + h;
  }
  return 0.0;
}
print(ochoArgs(1, 0.5, 2, "x", 3, 0.25, true, 4));
print(ochoArgs(ochoArgs(1, 1.0, 1, "y", 1, 1.0, true, 1) > 5.0 ? 10 : 20, 0.0, 0, "z", 0, 0.0, true, 0));

let errores: integer = 0;
let valores: integer[] = [5, 0, 2, 0, 1];
for (let i: integer = 0; i < 7; i = i + 1) {
  try {
    let q: integer = 100 / valores[i];
    if (q > 30) {
      continue;
    }
    print(q);
  } catch (e) {
    errores = errores + 1;
    print(e);
  }
}
print(errores);
