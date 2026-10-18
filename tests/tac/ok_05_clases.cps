// @esperado: TAC
// @genera: I14, I15
// Creacion de objetos, constructor, atributos con valor inicial, metodos,
// this explicito e implicito, y this desde una funcion anidada en un metodo.

class Punto {
  let x: integer;
  let y: integer = 0;
  let nombre: string = "origen";

  function constructor(x: integer, y: integer) {
    this.x = x;
    this.y = y;
  }

  function mover(dx: integer): integer {
    this.x = this.x + dx;
    return this.x;
  }

  function norma(): integer {
    return x * x + y * y;
  }

  function escalar(k: integer): integer {
    function aplicar(): integer {
      return this.x * k;
    }
    return aplicar();
  }
}

let p: Punto = new Punto(3, 4);
p.mover(2);
p.nombre = "p";
print(p.norma());
print(p.escalar(3));
print(p.nombre);
