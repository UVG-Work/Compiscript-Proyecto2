// @esperado: TAC
// @genera: I13, I24
// Acceso a una variable dos niveles arriba por la cadena de enlaces de acceso,
// y funciones guardadas en variables y llamadas a traves de ellas.

function externa(base: integer): integer {
  let total: integer = base;

  function media(n: integer): integer {
    function interna(k: integer): integer {
      total = total + k;
      return total;
    }
    return interna(n) + interna(n);
  }

  let g = media;
  return g(1);
}

function doble(x: integer): integer {
  return x * 2;
}

let f = doble;
print(f(21));
print(externa(10));
