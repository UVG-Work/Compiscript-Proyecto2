// @esperado: OK
// @regla: T1, T3, T4, T6, F1, F2
// float: literales, promocion integer -> float y aritmetica mixta.

let exacto: float = 1.5;
let promovido: float = 2;                 // integer promueve a float
let inferido = 3.25;
let mixto: float = 1 + 2.5;               // integer + float -> float
let producto: float = 2.0 * 3;

let entero: integer = 7;
let dividido: float = entero / 2.0;

let medidas: float[] = [1.5, 2, 3.75];    // la lista unifica en float
let elegido: float = exacto > 1.0 ? exacto : promovido;

function promedio(valores: float[]): float {
  let suma: float = 0.0;
  foreach (v in valores) {
    suma = suma + v;
  }
  return suma / 3.0;
}

if (exacto < promovido) {
  print("menor");
}

print(inferido);
print(mixto);
print(producto);
print(dividido);
print(medidas[0]);
print(elegido);
print(promedio(medidas));
