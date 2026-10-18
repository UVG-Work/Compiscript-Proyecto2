// @esperado: ERRORES 4
// @regla: K4, K5
// Sobrescritura con otra firma y metodos usados como valor.

class Animal {
  let nombre: string;

  function hablar(): string {
    return this.nombre;
  }

  function comer(cantidad: integer): integer {
    return cantidad;
  }

  function mostrar(): string {
    let h = hablar;               // metodo como valor sin llamarlo
    return this.nombre;
  }
}

class Perro : Animal {
  function hablar(volumen: integer): string {   // otro parametro
    return "guau";
  }

  function comer(cantidad: integer): string {   // otro retorno
    return "mucho";
  }
}

let perro: Perro = new Perro();
let accion = perro.hablar;          // metodo como valor sin llamarlo
print(perro.nombre);
