// @esperado: OK
// @regla: K6
// Una clase local usa sus propios atributos, sus metodos y los globales.

let factor: integer = 3;

function crear(): integer {
  class Contador {
    let cuenta: integer = 0;

    function sumar(n: integer): integer {
      this.cuenta = this.cuenta + n * factor;
      return actual();
    }

    function actual(): integer {
      return cuenta;
    }
  }

  let c: Contador = new Contador();
  return c.sumar(2);
}

print(crear());
