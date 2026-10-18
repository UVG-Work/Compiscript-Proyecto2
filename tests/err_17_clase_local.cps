// @esperado: ERRORES 3
// @regla: K6
// Una clase declarada dentro de una funcion no puede usar las variables ni las
// funciones locales de esa funcion: sus metodos se invocan por la tabla de
// metodos y no reciben enlace de acceso hacia ese marco.

function f(): integer {
  let k: integer = 7;

  function doble(n: integer): integer {
    return n * 2;
  }

  class A {
    let x: integer = k;           // valor inicial con una local de f

    function m(): integer {
      return doble(this.x);       // funcion anidada de f
    }

    function reiniciar(): integer {
      k = 1;                      // asigna una local de f
      return 0;
    }
  }

  let a: A = new A();
  return a.m() + a.reiniciar();
}

print(f());
