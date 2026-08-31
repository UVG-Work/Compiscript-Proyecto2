// @esperado: OK
// @regla: K1, K2, K3, A5
// Clases: atributos, constructor, this, metodos, herencia y sobreescritura.

class Animal {
  let nombre: string;
  let patas: integer;

  function constructor(nombre: string, patas: integer) {
    this.nombre = nombre;
    this.patas = patas;
  }

  function hablar(): string {
    return this.nombre + " hace ruido.";
  }

  function describir(): string {
    return this.hablar();
  }
}

class Perro : Animal {
  function constructor(nombre: string) {
    this.nombre = nombre;
    this.patas = 4;
  }

  function hablar(): string {
    return this.nombre + " ladra.";
  }
}

let animal: Animal = new Animal("Gato", 4);
let perro: Perro = new Perro("Toby");
let comoAnimal: Animal = perro;

print(animal.hablar());
print(perro.hablar());
print(perro.describir());
print(comoAnimal.nombre);
print(perro.patas);
