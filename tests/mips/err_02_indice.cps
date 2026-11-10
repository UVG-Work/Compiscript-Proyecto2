// @esperado: MIPS-ERROR
// @mips: M17, M14
// Indice fuera de rango sin try, desde una funcion anidada.

function leer(lista: integer[], i: integer): integer {
  function dentro(): integer {
    return lista[i];
  }
  return dentro();
}

let datos: integer[] = [10, 20, 30];
print(leer(datos, 2));
print(leer(datos, 3));
print("nunca");
