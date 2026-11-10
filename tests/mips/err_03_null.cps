// @esperado: MIPS-ERROR
// @mips: M17
// Metodo sobre un objeto null: el acceso a su tabla de metodos falla.

class Saludo {
  function decir(): string {
    return "hola";
  }
}

let s: Saludo = new Saludo();
print(s.decir());
s = null;
print(s.decir());
