// @esperado: OK
// @regla: C1, C2, T3
// Control de flujo: if/else, while, do-while, for, foreach, break, continue,
// switch y try/catch.

let i: integer = 0;
let total: integer = 0;
let notas: integer[] = [90, 45, 100];

if (total == 0) {
  print("vacio");
} else {
  print("con datos");
}

while (i < 3) {
  i = i + 1;
}

do {
  i = i - 1;
} while (i > 0);

for (let j: integer = 0; j < 3; j = j + 1) {
  if (j == 1) {
    continue;
  }
  print(j);
}

foreach (nota in notas) {
  if (nota == 100) {
    break;
  }
  total = total + nota;
}

switch (total == 135) {
  case true:
    print("la suma esperada");
  case false:
    print("otra suma");
  default:
    print("otro");
}

try {
  print(notas[0]);
} catch (err) {
  print("fallo: " + err);
}

print(total);
