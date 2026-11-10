// @esperado: MIPS
// @mips: M03, M05, M06
// Saltos: for con continue, while(true) con break, do-while, bucles anidados
// donde break sale solo del interno, y switch con caida, con return en sus
// casos y sobre un string.

let suma: integer = 0;
for (let i: integer = 1; i <= 10; i = i + 1) {
  if (i % 2 == 0) {
    continue;
  }
  suma = suma + i;
}
print(suma);

let k: integer = 0;
while (true) {
  k = k + 1;
  if (k > 3) {
    break;
  }
}
print(k);

do {
  k = k - 1;
} while (k > 0);
print(k);

let pares: integer = 0;
for (let i: integer = 0; i < 4; i = i + 1) {
  for (let j: integer = 0; j < 4; j = j + 1) {
    if (j > i) {
      break;
    }
    pares = pares + 1;
  }
}
print(pares);

function nombre(d: integer): string {
  switch (d) {
    case 1:
      return "lunes";
    case 2:
      return "martes";
    default:
      return "otro";
  }
}
print(nombre(2));
print(nombre(9));

let cuenta: integer = 0;
switch (2) {
  case 1:
    cuenta = cuenta + 1;
  case 2:
    cuenta = cuenta + 10;
  case 3:
    cuenta = cuenta + 100;
  default:
    cuenta = cuenta + 1000;
}
print(cuenta);

let saludo: string = "ho" + "la";
switch (saludo) {
  case "adios":
    print("se va");
    break;
  case "hola":
    print("llega");
    break;
}
