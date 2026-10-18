// @esperado: TAC
// @genera: I05, I06, I07, I08, I09, I10
// Sentencias de control; break y continue saltan al bucle que los contiene,
// tambien desde dentro de un switch.

let total: integer = 0;
let i: integer = 0;

if (total == 0) {
  total = 1;
} else {
  total = 2;
}

while (i < 5) {
  i = i + 1;
  if (i == 2) {
    continue;
  }
  total = total + i;
}

do {
  i = i - 1;
} while (i > 0);

for (let j: integer = 0; j < 10; j = j + 1) {
  if (j == 3) {
    continue;
  }
  switch (j) {
    case 1:
      total = total + 10;
      break;
    case 7:
      break;
    default:
      total = total + 1;
  }
  if (j == 8) {
    break;
  }
}

while (true) {
  total = total - 1;
  if (total < 0) {
    break;
  }
}

print(total);
