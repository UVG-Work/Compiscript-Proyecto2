# Compiscript — Compilador a MIPS

Proyecto de Construcción de Compiladores (UVG). Compilador de Compiscript, un
subconjunto de TypeScript, construido sobre ANTLR 4 y Java 17: análisis
semántico, código de tres direcciones (TAC) y generación de ensamblador MIPS
que se ejecuta en el simulador MARS.

- Lenguaje fuente: [`docs/Especificaciones.md`](docs/Especificaciones.md)
- Lenguaje intermedio, con ejemplos de traducción y supuestos:
  [`docs/CodigoIntermedio.md`](docs/CodigoIntermedio.md)
- Generación de MIPS: secuencia de llamada, `getReg()` y runtime:
  [`docs/Mips.md`](docs/Mips.md)
- Arquitectura y decisiones de diseño: [`docs/Arquitectura.md`](docs/Arquitectura.md)

## Requisitos

- JDK 17 o superior
- Maven (o usar el wrapper `./mvnw` incluido, que lo descarga solo)
- Graphviz, opcional: sin él `--dot` deja el `.dot` pero no genera el `.png`
- El simulador MARS 4.5 viene incluido en [`lib/Mars4_5.jar`](lib) (licencia
  MIT). Para usar otra copia: `-Dmars.jar=ruta` o la variable `MARS_JAR`.

## Compilar

```bash
./mvnw clean verify         # Linux / macOS
mvnw.cmd clean verify       # Windows
```

Genera `target/compiscript.jar`, con el runtime de ANTLR incluido, y **corre la
batería de pruebas contra ese jar**: si una regla se rompe o si el jar sale con
clases corruptas, el build falla en vez de entregar un artefacto malo.

Para una compilación rápida sin pruebas, `clean package`.

> Si el proyecto está abierto en un editor con soporte de Java (VS Code, Eclipse,
> IntelliJ), su compilador incremental puede escribir en `target/classes` **al
> mismo tiempo** que Maven empaqueta, y el jar sale con clases a medio compilar
> que revientan en ejecución con `java.lang.Error: Unresolved compilation
> problems`. Por eso el build corre la batería contra el jar ya empaquetado: es
> lo que convierte esa corrupción silenciosa en un fallo visible. Si aparece,
> basta con repetir `clean verify` con el editor cerrado.

## Ejecutar

```bash
java -jar target/compiscript.jar examples/ok_basico.cps
```

| Bandera | Efecto |
|---------|--------|
| `--tree` | imprime el árbol de parseo en texto |
| `--tokens` | imprime el flujo de tokens del lexer |
| `--dot` | exporta el árbol a `.dot` y, si hay Graphviz, a `.png` |
| `--quiet` | no imprime la tabla de símbolos ni los registros de activación |
| `--tac` | imprime solo el código intermedio (los errores van a stderr) |
| `--mips` | imprime solo el ensamblador MIPS (los errores van a stderr) |
| `--run` | genera el MIPS, lo ejecuta en MARS e imprime lo que imprime el programa |
| `--test <dir>` | corre la batería de pruebas y las matrices de cobertura |
| `--ide [archivo]` | abre el IDE gráfico |

Códigos de salida: `0` sin errores, `1` con errores, `2` uso inválido. Los tres
los distinguen el arnés de pruebas y cualquier CI.

Si el programa no tiene errores, imprime en este orden:

1. La tabla de símbolos, entorno por entorno, con la dirección de cada símbolo
   (`G[n]`, `fp[n]`, `+n` en un objeto o la etiqueta de una función) y el
   tamaño del registro de activación de cada función.
2. El código intermedio.
3. El área estática, el registro de activación de cada función y la forma de
   cada objeto.

```
$ java -jar target/compiscript.jar tests/ok_03_funciones.cps
[FUNCTION] funcion crearContador (profundidad 1, 4 bytes)
  function siguiente      () => integer   ...  dir crearContador.siguiente  0 param, marco 48 bytes, captura [cuenta]
  var      cuenta         integer         ...  dir fp[32]          capturada
```

```
$ java -jar target/compiscript.jar tests/tac/ok_03_funciones.cps --tac
func factorial, 44
    ifFalse fp[32] <= 1 goto L1
    return 1                        ; return 1;
L1:
    t1 = fp[32] - 1                 ; return n * factorial(n - 1);
    param t1
    t1 = call factorial, 1
    t1 = fp[32] * t1
    return t1
endfunc
```

Para ejecutar un programa en el simulador:

```
$ java -jar target/compiscript.jar tests/mips/ok_06_closures.cps --run
25
49
230
10
```

`--mips` deja el ensamblador listo para abrirlo en MARS:

```bash
java -jar target/compiscript.jar tests/mips/ok_05_funciones.cps --mips > funciones.asm
java -jar lib/Mars4_5.jar funciones.asm       # o abrirlo en la interfaz de MARS
```

Un error en tiempo de ejecución (división entre cero, índice fuera de rango,
acceso a `null`) salta al `catch` que lo rodea; sin `catch`, el programa
imprime el error y `--run` termina con código 1:

```
$ java -jar target/compiscript.jar tests/mips/err_01_division.cps --run
antes
Error en tiempo de ejecucion: division entre cero
```

Con errores no se genera código intermedio ni MIPS. Los diagnósticos van con línea,
columna, categoría y severidad:

```
$ java -jar target/compiscript.jar tests/err_01_tipos.cps --quiet
Errores semanticos (8):
  linea 8:0 [TIPOS/error] no se puede inicializar 'malo' de tipo integer con un valor string
  linea 9:22 [TIPOS/error] no se puede aplicar '+' a integer y string
  ...
```

## El IDE

```bash
java -jar target/compiscript.jar --ide                   # vacío
java -jar target/compiscript.jar --ide tests/ok_07_completo.cps
```

Editor con resaltado y números de línea a la izquierda; a la derecha las pestañas
**Árbol sintáctico** (diagrama de nodos y aristas, con zoom y exportación a
DOT/PNG), **Tabla de símbolos** (árbol de entornos y, al seleccionar uno, sus
símbolos con todos los atributos, incluida su dirección), **Tokens**, **Código
intermedio**, **Registros de activación** y **MIPS**. Abajo, la tabla de
**Errores** —sintaxis y semántica juntas, con severidad, categoría y
ubicación— y la **Salida**, donde aparece también lo que imprime el programa al
ejecutarlo en MARS. Con errores, las pestañas de código quedan vacías.

| Atajo | Acción |
|---|---|
| `F5` | Compilar |
| `F7` | Compilar y ejecutar el MIPS en MARS |
| `F6` | Ejecutar la batería de pruebas |
| doble clic en un error | salta a esa línea del editor |

Las líneas con error quedan pintadas de rojo en el editor y las que tienen
advertencia, de ámbar.

## Pruebas

```bash
java -jar target/compiscript.jar --test tests   # a mano
./mvnw clean verify                             # como parte del build
```

Cada `.cps` de [`tests/`](tests/) declara en su cabecera qué espera y qué reglas
del enunciado ejercita:

```
// @esperado: ERRORES 8
// @regla: T1, T2, T3, T4, T5, C1
```

Se compara el número **exacto** de errores, no solo si hubo alguno: así la
batería se rompe en los dos sentidos, cuando una regla deja de detectarse y
cuando aparece un falso positivo nuevo. Los casos `ok_` importan tanto como los
`err_`: son los que atrapan los falsos positivos.

Los casos de código intermedio viven en [`tests/tac/`](tests/tac). Cada uno
tiene al lado su `.tac` esperado y declara qué construcciones traduce:

```
// @esperado: TAC
// @genera: I11, I12, I13, I18
```

La batería compara el TAC generado línea por línea y, si difiere, señala la
primera línea distinta. También comprueba que ningún programa con errores
produzca código intermedio. Para regenerar un `.tac` después de un cambio
intencional:

```bash
java -jar target/compiscript.jar tests/tac/ok_05_clases.cps --tac > tests/tac/ok_05_clases.tac
```

y revisarlo a mano contra el fuente antes de commitearlo.

Los casos de MIPS viven en [`tests/mips/`](tests/mips). Cada uno se compila,
**se ejecuta en MARS** y su salida se compara línea por línea con el `.out` de
al lado:

```
// @esperado: MIPS
// @mips: M07, M08, M09
```

Los `err_` de esa carpeta declaran `@esperado: MIPS-ERROR`: deben terminar con
un error en tiempo de ejecución y su `.out` incluye el mensaje. Para regenerar
un `.out`:

```bash
java -jar target/compiscript.jar tests/mips/ok_05_funciones.cps --run > tests/mips/ok_05_funciones.out
```

y revisarlo a mano contra el fuente antes de commitearlo.

Al final imprime tres **matrices de cobertura**: las 30 reglas semánticas, las
24 construcciones que se traducen a TAC y las 20 de la generación de MIPS.
**Una regla o construcción sin cubrir hace fallar la batería** igual que una
prueba en rojo:

```
Cobertura de las 30 reglas semanticas:
  T1  [ok |err]  aritmetica (+,-,*,/) sobre integer o float
  ...
Cobertura de la generacion de codigo intermedio (24 construcciones):
  I01 [tac]  aritmetica (+,-,*,/,%) con precedencia y asociatividad
  ...
  casos fallidos: 17 programa(s) con errores, ninguno genero codigo intermedio
  todas las construcciones tienen su caso

Cobertura de la generacion de MIPS (20 construcciones, ejecutadas en MARS):
  M01 [run]  aritmetica entera, division y modulo
  ...
  casos fallidos: 17 programa(s) con errores sin ensamblador, 3 con error en tiempo de ejecucion
  todas las construcciones tienen su caso
```

## Con Docker

```bash
docker compose up -d --build
docker compose exec compiscript ./mvnw clean verify
docker compose exec compiscript java -jar target/compiscript.jar --test tests
docker compose exec compiscript java -jar target/compiscript.jar examples/ok_basico.cps --dot
docker compose exec compiscript java -jar target/compiscript.jar tests/tac/ok_06_herencia.cps --tac
docker compose exec compiscript java -jar target/compiscript.jar tests/mips/ok_07_clases.cps --run
```

La imagen trae Graphviz, así que ahí `--dot` sí produce el `.png`.

## Estructura

```
src/main/antlr4/com/uvg/compiscript/parser/
  Compiscript.g4            gramática oficial; único cambio: float en baseType y FloatLiteral
src/main/java/com/uvg/compiscript/
  Main.java                 driver de línea de comandos
  SyntaxErrorListener.java  acumula los errores de sintaxis en vez de escribirlos a stderr
  types/                    sistema de tipos
  symbols/                  tabla de símbolos, ámbitos y distribución de memoria
  semantic/                 analizador: SemanticVisitor, TypeRules, anotaciones del árbol
  codegen/                  generador de TAC, temporales y registros de activación
  mips/                     generador de MIPS, getReg, runtime y ejecución en MARS
  compiler/                 CompilerService: todas las fases en un solo lugar
  ide/                      IDE Swing
  tools/                    exportador DOT, arnés de pruebas y catálogos de cobertura
examples/                   programas .cps de ejemplo
tests/                      batería semántica
tests/tac/                  batería de código intermedio (.cps y su .tac esperado)
tests/mips/                 batería de MIPS (.cps y la salida .out esperada en MARS)
lib/Mars4_5.jar             simulador MARS 4.5
```

Los archivos que ANTLR genera (`CompiscriptLexer.java`, `CompiscriptParser.java`,
`CompiscriptVisitor.java`, `CompiscriptBaseVisitor.java`) viven en `target/` y no
se versionan: los regenera `mvnw package` a partir de la gramática.

## Nota sobre Docker

El CLI, la batería y MARS corren dentro del contenedor. El **IDE no**: la
imagen no trae servidor gráfico, así que se demuestra en el anfitrión.
