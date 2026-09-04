// @esperado: ERRORES 3
// @regla: A3, K2, F2
// Dos regresiones sobre a quien se culpa y cuantas veces.
//
// 1. hoist() registra clases y funciones antes de recorrer las sentencias, asi
//    que con `let dato` arriba y `class dato` abajo, la clase gana el nombre y
//    la que fallaba al definirse era la variable: el error salia sobre la linea
//    1, que esta perfectamente escrita. Ahora se culpa a la que sobra, la de
//    mas abajo. NOTA: el arnes compara cantidades, no posiciones; esta prueba
//    fija que sea UN error, la linea correcta se verifica a ojo.
//
// 2. Un constructor con tipo de retorno daba DOS mensajes, y el segundo decia
//    "no declara tipo de retorno" sobre un constructor que si lo declaraba.
//    Es una sola equivocacion del programador y ahora es un solo mensaje.

let dato: integer = 1;
print(dato);

class dato {                              // la redeclaracion es esta, no la de arriba
  let n: integer;
  function constructor(): integer {       // un solo error, y dice lo correcto
    this.n = 0;
    return 1;
  }
}

// Un constructor que devuelve un valor SIN declarar tipo sigue siendo su propio
// error: son equivocaciones distintas.
class Otra {
  let m: integer;
  function constructor() { this.m = 0; return 1; }
}
print(new Otra().m);
