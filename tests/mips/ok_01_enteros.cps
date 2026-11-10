// @esperado: MIPS
// @mips: M01, M20
// Aritmetica entera de 32 bits: precedencia, division que trunca hacia cero,
// modulo con el signo del dividendo y desborde. Imprime un valor de cada tipo.

let a: integer = 17;
let b: integer = 5;
const K: integer = 1000;

print(a + b * 2);
print((a + b) * 2);
print(a - b - 3);
print(a / b);
print(a % b);
print(-a / b);
print(-a % b);
print(a * -b);
print(K * K);

let grande: integer = 2147483647;
print(grande + 1);

let c: integer = a;
c = c * c - 1;
print(c);

let nada: integer[] = null;
print(true);
print(false);
print(nada);
print("texto");
print(2.5);
