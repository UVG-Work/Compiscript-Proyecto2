// @esperado: OK
// @regla: K4, K5
// Sobrescritura que conserva la firma y metodos siempre invocados.

class Figura {
  let lado: integer;

  function constructor(lado: integer) {
    this.lado = lado;
  }

  function area(): integer {
    return 0;
  }

  function describir(): integer {
    return area() + this.area();
  }
}

class Cuadrado : Figura {
  function constructor(lado: integer) {
    this.lado = lado;
  }

  function area(): integer {
    return this.lado * this.lado;
  }
}

let figura: Figura = new Cuadrado(3);
print(figura.area());
print(figura.describir());
