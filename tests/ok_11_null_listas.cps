// @esperado: OK
// @regla: L1, T6, K1
// El lado bueno de err_14: donde null SI debe caber.

class C { let n: integer; function constructor(){ this.n = 0; } }

let vacia: integer[] = [];             // [] cabe en cualquier lista
let anidada: integer[][] = [[], []];   // tambien anidada
let objetos: C[] = [null];             // el elemento es una clase: admite null
let listas: integer[][] = [null];      // el elemento es integer[]: tambien
let mixta: C[] = [new C(), null];      // objeto y null conviven

function recibe(l: integer[]): integer { return l.length; }

print(recibe([]));
print(vacia.length);
print(anidada.length);
print(objetos.length);
print(listas.length);
print(mixta.length);
