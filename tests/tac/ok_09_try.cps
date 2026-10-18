// @esperado: TAC
// @genera: I21
// try/catch: el rango protegido y el manejador que recibe el mensaje.

let lista: integer[] = [1, 2, 3];
let i: integer = 5;

try {
  print(lista[i]);
} catch (error) {
  print("fallo: " + error);
}
print("fin");
