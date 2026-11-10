// @esperado: MIPS
// @mips: M02
// Punto flotante en la FPU: operaciones, promocion de integer, negacion,
// comparaciones como valor y como salto, y float en parametros y retorno.

let x: float = 7.5;
let n: integer = 2;

print(x + n);
print(x - 0.5);
print(x * n);
print(x / n);
print(-x);
print(n / 4);

let r: float = n;
print(r / 4);

print(x > n);
print(x <= 7.5);
print(x == 7.5);
print(x != 7.5);
print(n < x);

if (x >= 8) {
  print("mayor");
} else {
  print("menor");
}

function area(radio: float): float {
  return 3.5 * radio * radio;
}

print(area(2));
print(area(0.5) < 1.0);
