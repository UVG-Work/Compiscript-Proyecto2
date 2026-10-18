// @esperado: TAC
// @genera: I19
// Herencia: la subclase extiende la forma del padre, hereda su constructor y
// su inicializador, y la llamada se despacha por la tabla de metodos.

class Animal {
  let nombre: string;
  let sonido: string = "...";

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " dice " + this.sonido;
  }

  function presentar(): string {
    return "Soy " + this.nombre + ". " + this.hablar();
  }
}

class Perro : Animal {
  let raza: string = "mestizo";

  function hablar(): string {
    return this.nombre + " ladra";
  }
}

let a: Animal = new Perro("Toby");
let b: Animal = new Animal("Gato");
print(a.hablar());
print(a.presentar());
print(b.hablar());
