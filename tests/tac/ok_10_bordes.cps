// @esperado: TAC
// @genera: I16, I22
// Casos borde: indices constantes enormes, listas literales de float con
// elementos enteros, y nombres que podrian chocar con las etiquetas que
// inventa el compilador (main, Clase.init) o entre si (funciones homonimas en
// bloques hermanos).

function lejos(a: integer[]): integer {
  return a[99999999999] + a[600000000];
}

function main(): integer {
  return 1;
}

function elegir(c: boolean): integer {
  if (c) {
    function h(): integer { return 10; }
    return h();
  } else {
    function h(): integer { return 20; }
    return h();
  }
}

class Caja {
  let valor: integer = 5;
  function init(): integer {
    return this.valor;
  }
}

let n: integer = 3;
let reales: float[] = [1, 2.5, n];
let matriz: float[][] = [[1, 2], [3.5]];
let caja: Caja = new Caja();

print(matriz[0][1]);
print(reales[2]);
print(caja.init());
print(main());
print(elegir(true));
