// @esperado: OK
// @regla: A1, A2, A3, A4, A5
// Ambito: bloques anidados, sombreado y acceso a globales desde una funcion.

let comun: integer = 1;

function usaGlobal(): integer {
  return comun + 1;
}

{
  let comun: string = "sombra";
  print(comun);
}

{
  let interno: integer = 2;
  {
    let interno: string = "mas adentro";
    print(interno);
  }
  print(interno);
}

function conParametro(comun: string): string {
  return comun;
}

print(usaGlobal());
print(conParametro("un parametro que sombrea"));
