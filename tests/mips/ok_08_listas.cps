// @esperado: MIPS
// @mips: M14, M05
// Listas de cada tipo de elemento, matrices, escritura con indice variable,
// foreach con continue y length.

let banderas: boolean[] = [true, false, true];
let reales: float[] = [1.5, 2, 3.25];
let nombres: string[] = ["ana", "beto", "carla"];
let matriz: integer[][] = [[1, 2, 3], [4, 5, 6]];

let suma: float = 0.0;
foreach (r in reales) {
  suma = suma + r;
}
print(suma);

print(banderas[1]);
banderas[1] = banderas[0];
print(banderas[1]);

let total: integer = 0;
for (let i: integer = 0; i < matriz.length; i = i + 1) {
  for (let j: integer = 0; j < matriz[i].length; j = j + 1) {
    total = total + matriz[i][j] * (i + 1);
  }
}
print(total);

foreach (n in nombres) {
  if (n == "beto") {
    continue;
  }
  print(n);
}

let cuadrados: integer[] = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
for (let i: integer = 0; i < 10; i = i + 1) {
  cuadrados[i] = i * i;
}
print(cuadrados[9] + cuadrados[3]);
print(cuadrados.length);

let vacia: string[] = [];
print(vacia.length);
