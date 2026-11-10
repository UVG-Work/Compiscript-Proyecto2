// @esperado: MIPS-ERROR
// @mips: M17
// Division entre cero sin try: el programa imprime el error y termina con
// codigo 1 en vez de seguir.

function promedio(total: integer, n: integer): integer {
  return total / n;
}

print("antes");
print(promedio(10, 0));
print("nunca");
