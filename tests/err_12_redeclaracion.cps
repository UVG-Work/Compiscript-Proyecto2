// @esperado: ERRORES 2
// @regla: F5, G3
// Regresion: una funcion redeclarada debe producir un mensaje, no dos.
//
// visitFunctionDeclaration resolvia el simbolo por nombre, y de un nombre
// duplicado el ambito solo conserva el primero: el cuerpo de la segunda
// declaracion se verificaba contra los parametros de la primera y sus propios
// parametros salian como "no declarado". Justo la regla F5 que el enunciado
// pide era la que reportaba de mas.

function f(a: integer): integer {
  return a;
}

function f(texto: string): string {   // ya esta declarada: un solo error
  return texto;                       // 'texto' SI esta declarado aqui
}

class C {
  let n: integer;
  function constructor() { this.n = 0; }
  function m(a: integer): integer { return a; }
  function m(b: string): string { return b; }   // miembro duplicado, un error
}

print(f(1));
print(new C().m(1));
