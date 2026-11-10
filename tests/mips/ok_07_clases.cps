// @esperado: MIPS
// @mips: M12, M13
// Objetos: inicializadores heredados, constructores propios, despacho por la
// tabla de metodos sobre una lista de la clase base, atributos sin this y
// this desde una funcion anidada en un metodo.

class Figura {
  let nombre: string = "figura";
  let lados: integer = 0;

  function area(): float {
    return 0.0;
  }

  function describir(): string {
    return this.nombre + " de area";
  }
}

class Rect : Figura {
  let ancho: float;
  let alto: float;

  function constructor(a: float, b: float) {
    this.ancho = a;
    this.alto = b;
    this.nombre = "rect";
    this.lados = 4;
  }

  function area(): float {
    return this.ancho * this.alto;
  }
}

class Cuadrado : Rect {
  function constructor(lado: float) {
    this.ancho = lado;
    this.alto = lado;
    this.nombre = "cuadrado";
    this.lados = 4;
  }
}

class Circulo : Figura {
  let r: float;

  function constructor(r: float) {
    this.r = r;
    this.nombre = "circulo";
  }

  function area(): float {
    return 3.0 * this.r * this.r;
  }
}

let figuras: Figura[] = [new Rect(2, 3), new Cuadrado(4), new Circulo(1), new Figura()];
let total: float = 0.0;
foreach (f in figuras) {
  print(f.describir());
  print(f.area());
  total = total + f.area();
}
print(total);
print(figuras[1].lados);

class Cuenta {
  let saldo: integer = 100;
  let activa: boolean = true;

  function depositar(monto: integer): integer {
    saldo = saldo + monto;
    return saldo;
  }

  function retirarVarias(monto: integer, veces: integer): integer {
    function una(): integer {
      return this.depositar(-monto);
    }
    let i: integer = 0;
    while (i < veces) {
      una();
      i = i + 1;
    }
    return this.saldo;
  }
}

let c: Cuenta = new Cuenta();
c.depositar(50);
print(c.retirarVarias(30, 3));
print(c.activa);
