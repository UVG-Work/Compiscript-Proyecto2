// @esperado: MIPS
// @mips: M03, M04
// Cortocircuito: el contador de llamadas muestra que el segundo operando
// solo se evalua cuando hace falta. Tambien el ternario.

let llamadas: integer = 0;

function marca(v: boolean): boolean {
  llamadas = llamadas + 1;
  return v;
}

let r: boolean = marca(false) && marca(true);
print(r);
print(llamadas);

r = marca(true) || marca(false);
print(r);
print(llamadas);

r = !(marca(true) && marca(false)) || marca(true);
print(r);
print(llamadas);

if (llamadas > 3 && !(llamadas == 5)) {
  print("cuatro llamadas");
}

let a: integer = 3;
print(a > 1 && a < 5);
print(a < 1 || a > 5);
print(a > 2 ? "grande" : "chico");
print(a > 2 ? 1.5 : a);
