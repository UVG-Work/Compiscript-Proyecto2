// @esperado: OK
// @regla: C1, C2, T3, A5
// switch sobre un selector que no es boolean, y break saliendo de un case.
//
// El enunciado lista al switch junto a las condiciones boolean. Aqui la regla
// se cumple exigiendo que cada 'case' sea comparable con el selector: eso es lo
// que le da sentido semantico. Exigir boolean dejaria como unico switch
// escribible `switch (x == k) { case true: ... }`, que es un if disfrazado.

let dia: integer = 3;

switch (dia) {
  case 1: { print("lunes"); break; }
  case 2: { print("martes"); break; }
  default: { print("otro dia"); }
}

let clave: string = "beta";

switch (clave) {
  case "alfa": { print("primero"); break; }
  case "beta": { print("segundo"); break; }
  default: { print("ninguno"); }
}

// break pertenece al switch; continue sigue siendo del bucle que lo envuelve.
let i: integer = 0;
while (i < 3) {
  i = i + 1;
  switch (i) {
    case 2: { break; }
    default: { print(i); }
  }
  continue;
}

// El switch tambien es un entorno propio: esta 'i' no choca con la de arriba.
switch (dia) {
  case 3: {
    let i: string = "otra i, otro ambito";
    print(i);
  }
}
