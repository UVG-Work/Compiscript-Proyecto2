// @esperado: ERRORES 9
// @regla: F1, F2, F5, C3, G3
// Firmas, argumentos y retornos.

function suma(a: integer, b: integer): integer {
  return a + b;
}

function malRetorno(): integer {
  print("no retorna nada");     // hay caminos que no retornan
}

function vacia() {
  return 1;                     // devuelve un valor sin declarar retorno
}

function duplicada(): integer {
  return 1;
}

function duplicada(): integer { // ya esta declarada
  return 2;
}

function repite(a: integer, a: integer): integer {  // parametro repetido
  return a;
}

print(suma(1));                 // faltan argumentos
print(suma(1, "dos"));          // tipo de argumento incorrecto
print(suma(1, 2, 3));           // sobran argumentos

let n: integer = 5;
print(n(1));                    // n no es una funcion

print(malRetorno());
print(vacia());
print(duplicada());
print(repite(1, 2));

return 1;                       // return fuera de una funcion
