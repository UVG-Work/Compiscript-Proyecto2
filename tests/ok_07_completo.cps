// @esperado: OK
// @regla: T1, T3, T4, T5, T6, A1, A4, A5, F1, F2, F4, C1, C2, C3, K1, K2, K3, L1, L2, G3
// Programa completo: todas las construcciones del lenguaje trabajando juntas.

class Estudiante {
  let nombre: string;
  let notas: integer[];

  function constructor(nombre: string, notas: integer[]) {
    this.nombre = nombre;
    this.notas = notas;
  }

  function promedio(): integer {
    let suma: integer = 0;
    foreach (nota in this.notas) {
      suma = suma + nota;
    }
    if (this.notas.length == 0) {
      return 0;
    }
    return suma / this.notas.length;
  }

  function reporte(): string {
    return this.nombre + " esta " + textoDe(this.promedio());
  }
}

class Becado : Estudiante {
  function reporte(): string {
    return "[beca] " + this.nombre;
  }
}

const MINIMO: integer = 60;

function textoDe(valor: integer): string {
  if (valor >= MINIMO) {
    return "aprobado";
  }
  return "reprobado";
}

function contarAprobadas(notas: integer[]): integer {
  function esAprobada(nota: integer): boolean {
    return nota >= MINIMO;
  }

  let total: integer = 0;
  foreach (nota in notas) {
    if (esAprobada(nota)) {
      total = total + 1;
    }
  }
  return total;
}

function acumulador(): integer {
  let cuenta: integer = 0;

  function sumar(cuanto: integer): integer {
    cuenta = cuenta + cuanto;
    return cuenta;
  }

  sumar(2);
  return sumar(3);
}

let ana: Estudiante = new Estudiante("Ana", [90, 45, 100, 72]);
let luis: Becado = new Becado("Luis", [80, 70]);
let comoEstudiante: Estudiante = luis;

print(ana.reporte());
print(luis.reporte());
print(comoEstudiante.promedio());
print(contarAprobadas(ana.notas));
print(acumulador());

let intentos: integer = 0;
while (intentos < 3) {
  intentos = intentos + 1;
  if (intentos == 2) {
    continue;
  }
  print(intentos);
}

do {
  intentos = intentos - 1;
} while (intentos > 0);

switch (intentos == 0) {
  case true:
    print("sin intentos");
  default:
    print("quedan intentos");
}

try {
  print(ana.notas[0]);
} catch (err) {
  print("fallo: " + err);
}

{
  let interno: string = "ambito anidado";
  print(interno);
}
