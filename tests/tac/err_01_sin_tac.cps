// @esperado: ERRORES 2
// Un programa con errores no llega a la generacion: se reportan los errores
// y no se produce codigo intermedio.

function doble(x: integer): integer {
  return x * 2;
}

let valor: integer = doble("tres");
print(noDeclarada);
