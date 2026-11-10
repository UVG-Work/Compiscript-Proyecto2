// @esperado: MIPS
// @mips: M10, M11
// Enlace de acceso: funciones anidadas en dos niveles que leen y escriben las
// locales de la externa, recursion dentro de una anidada y funciones como
// valor, llamadas con jalr.

function contador(inicio: integer): integer {
  let cuenta: integer = inicio;

  function avanzar(paso: integer): integer {
    cuenta = cuenta + paso;
    return cuenta;
  }

  function dos(): integer {
    function tres(): integer {
      cuenta = cuenta * 2;
      return cuenta;
    }
    return tres() + avanzar(1);
  }

  avanzar(5);
  return dos();
}
print(contador(1));

function cuadrado(x: integer): integer {
  return x * x;
}

function aplicar(): integer {
  let f = cuadrado;
  return f(7);
}
print(aplicar());

function fabrica(base: integer): integer {
  function sumaBase(x: integer): integer {
    return x + base;
  }
  let g = sumaBase;
  return g(10) + g(20);
}
print(fabrica(100));

function externo(n: integer): integer {
  function interno(k: integer): integer {
    if (k == 0) {
      return n;
    }
    return 1 + interno(k - 1);
  }
  return interno(n);
}
print(externo(5));
