// Programa valido: clases, herencia, funcion anidada, arreglos y ambitos.

class Animal {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " hace ruido.";
  }
}

class Perro : Animal {
  function hablar(): string {
    return this.nombre + " ladra.";
  }
}

function contarAprobadas(notas: integer[]): integer {
  function esAprobada(nota: integer): boolean {
    return nota >= 60;
  }

  let total: integer = 0;
  foreach (nota in notas) {
    if (esAprobada(nota)) {
      total = total + 1;
    }
  }
  return total;
}

const MINIMO: integer = 60;

let notas: integer[] = [90, 45, 100, 72];
let perro: Perro = new Perro("Toby");

print(perro.hablar());
print(contarAprobadas(notas));

{
  let interno: string = "ambito anidado";
  print(interno);
}
