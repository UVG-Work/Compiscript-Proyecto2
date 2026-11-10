// @esperado: MIPS
// @mips: M15
// Cadenas en el heap: concatenacion en un bucle y en recursion, == y !=
// por contenido, y una literal con barra invertida.

let a: string = "hola";
let b: string = "ho" + "la";
print(a == b);
print(a != b);
print(a == "adios");

let frase: string = "";
for (let i: integer = 0; i < 3; i = i + 1) {
  frase = frase + "ab";
}
print(frase);
if (frase == "ababab") {
  print("iguales");
}

function repetir(s: string, n: integer): string {
  if (n == 0) {
    return "";
  }
  return s + repetir(s, n - 1);
}
print(repetir("xy", 4));

print("C:\temp");
