// @esperado: ERRORES 9
// @regla: K1, K2, K3, G3
// Clases, miembros y herencia.

class Animal {
  let nombre: string;
  let sinTipo;                  // el atributo debe declarar su tipo
  let nombre: integer;          // miembro duplicado

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre;
  }
}

class Perro : NoExiste {        // superclase no declarada
  function ladrar(): string {
    return "guau";
  }
}

class A : B { }                 // herencia circular
class B : A { }

let animal: Animal = new Animal("Gato");

print(animal.edad);             // miembro inexistente
animal.edad = 3;                // miembro inexistente
print(animal.hablar(1));        // el metodo no recibe argumentos

let otro: Animal = new Animal();  // faltan argumentos al constructor

this.nombre = "fuera";          // this fuera de una clase

print(otro.hablar());
