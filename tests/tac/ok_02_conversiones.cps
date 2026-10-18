// @esperado: TAC
// @genera: I22, I23
// Promocion de integer a float, concatenacion y operador ternario.

function mitad(x: float): float {
  return x / 2;
}

let entero: integer = 5;
let real: float = entero;
let suma: float = real + entero * 2;
let otra: float = mitad(entero);
let saludo: string = "hola" + " " + "mundo";
let signo: string = entero > 0 ? "positivo" : "negativo";
let limite: float = entero < 3 ? 1.5 : entero;

print(real);
print(suma);
print(otra);
print(saludo);
print(signo);
print(limite);
