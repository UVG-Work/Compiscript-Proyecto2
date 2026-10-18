// @esperado: TAC
// @genera: I01, I02, I03, I04, I17
// Precedencia, asociatividad, cortocircuito y reciclaje de temporales.

const BASE: integer = 10;
let a: integer = 7;
let b: integer = 3;

let c: integer = a + b * 2 - BASE / (a - b) % 4;
let d: integer = a - b - 1;
let e: integer = -a;
let mayor: boolean = a > b;
let rango: boolean = a >= 0 && a <= 10 || !(b != 3);
let igual: boolean = a == b;
c = c * (a + b);

print(c);
print(d);
print(e);
print(mayor);
print(rango);
print(igual);
