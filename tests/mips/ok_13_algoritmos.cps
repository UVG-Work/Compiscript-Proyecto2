// @esperado: MIPS
// @mips: M01, M02, M05, M09, M14
// Algoritmos completos: quicksort sobre una lista, busqueda binaria, criba,
// mcd, Ackermann, Collatz, potencia rapida, Fibonacci iterativo, recursion
// de 5000 niveles y producto de matrices de float. Lo esperado se verifico
// contra una implementacion independiente en Python.
let datos: integer[] = [38, 27, 43, 3, 9, 82, 10, 3, -5, 64, 0, 17, 99, 21, 8, 56, -12, 44, 7, 30];

function particion(a: integer[], lo: integer, hi: integer): integer {
  let pivote: integer = a[hi];
  let i: integer = lo - 1;
  for (let j: integer = lo; j < hi; j = j + 1) {
    if (a[j] <= pivote) {
      i = i + 1;
      let t: integer = a[i];
      a[i] = a[j];
      a[j] = t;
    }
  }
  let t2: integer = a[i + 1];
  a[i + 1] = a[hi];
  a[hi] = t2;
  return i + 1;
}

function quicksort(a: integer[], lo: integer, hi: integer) {
  if (lo < hi) {
    let p: integer = particion(a, lo, hi);
    quicksort(a, lo, p - 1);
    quicksort(a, p + 1, hi);
  }
}

quicksort(datos, 0, datos.length - 1);
let linea: string = "";
foreach (d in datos) {
  linea = linea + " " + texto(d);
}
print(linea);

function texto(n: integer): string {
  if (n < 0) {
    return "-" + texto(-n);
  }
  let digitos: string[] = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];
  if (n < 10) {
    return digitos[n];
  }
  return texto(n / 10) + digitos[n % 10];
}

function buscar(a: integer[], x: integer): integer {
  let lo: integer = 0;
  let hi: integer = a.length - 1;
  while (lo <= hi) {
    let m: integer = (lo + hi) / 2;
    if (a[m] == x) {
      return m;
    } else {
      if (a[m] < x) {
        lo = m + 1;
      } else {
        hi = m - 1;
      }
    }
  }
  return -1;
}
print(buscar(datos, 56));
print(buscar(datos, 57));

let criba: boolean[] = [true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true,
  true, true, true, true, true, true, true, true, true, true];
let primos: integer = 0;
for (let i: integer = 2; i < criba.length; i = i + 1) {
  if (criba[i]) {
    primos = primos + 1;
    let k: integer = i * i;
    while (k < criba.length) {
      criba[k] = false;
      k = k + i;
    }
  }
}
print(primos);

function mcd(a: integer, b: integer): integer {
  return b == 0 ? a : mcd(b, a % b);
}
print(mcd(1071, 462));

function ack(m: integer, n: integer): integer {
  if (m == 0) {
    return n + 1;
  }
  if (n == 0) {
    return ack(m - 1, 1);
  }
  return ack(m - 1, ack(m, n - 1));
}
print(ack(2, 3));
print(ack(3, 3));

function collatz(n: integer): integer {
  let pasos: integer = 0;
  while (n != 1) {
    n = n % 2 == 0 ? n / 2 : 3 * n + 1;
    pasos = pasos + 1;
  }
  return pasos;
}
print(collatz(27));

function potencia(b: integer, e: integer): integer {
  if (e == 0) {
    return 1;
  }
  let mitad: integer = potencia(b, e / 2);
  if (e % 2 == 0) {
    return mitad * mitad;
  }
  return mitad * mitad * b;
}
print(potencia(3, 19));

function fibIter(n: integer): integer {
  let a: integer = 0;
  let b: integer = 1;
  for (let i: integer = 0; i < n; i = i + 1) {
    let c: integer = a + b;
    a = b;
    b = c;
  }
  return a;
}
print(fibIter(40));

function sumaRec(n: integer): integer {
  if (n == 0) {
    return 0;
  }
  return n + sumaRec(n - 1);
}
print(sumaRec(5000));

let m1: float[][] = [[1.5, 2.0], [0.5, -1.0]];
let m2: float[][] = [[2.0, 0.0], [1.0, 4.0]];
let r: float[][] = [[0.0, 0.0], [0.0, 0.0]];
for (let i: integer = 0; i < 2; i = i + 1) {
  for (let j: integer = 0; j < 2; j = j + 1) {
    let s: float = 0.0;
    for (let k: integer = 0; k < 2; k = k + 1) {
      s = s + m1[i][k] * m2[k][j];
    }
    r[i][j] = s;
  }
}
print(r[0][0]);
print(r[0][1]);
print(r[1][0]);
print(r[1][1]);
