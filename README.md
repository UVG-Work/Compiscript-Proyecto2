# Compiscript — Análisis semántico y código intermedio

Proyecto de Construcción de Compiladores (UVG). Compilador de Compiscript, un
subconjunto de TypeScript, construido sobre ANTLR 4 y Java 17: análisis
semántico y generación de código de tres direcciones (TAC).

- Lenguaje fuente: [`docs/Especificaciones.md`](docs/Especificaciones.md)
- Lenguaje intermedio, con ejemplos de traducción y supuestos:
  [`docs/CodigoIntermedio.md`](docs/CodigoIntermedio.md)
- Arquitectura y decisiones de diseño: [`docs/Arquitectura.md`](docs/Arquitectura.md)

## Requisitos

- JDK 17 o superior
- Maven (o usar el wrapper `./mvnw` incluido, que lo descarga solo)
- Graphviz, opcional: sin él `--dot` deja el `.dot` pero no genera el `.png`

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

Con errores no se genera código intermedio. Los diagnósticos van con línea,
columna, categoría y severidad:

```
$ java -jar target/compiscript.jar tests/err_01_tipos.cps --quiet
Errores semanticos (8):
  linea 8:22 [TIPOS/error] no se puede aplicar '+' a integer y string
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
intermedio** y **Registros de activación**. Abajo, la tabla de **Errores**
—sintaxis y semántica juntas, con severidad, categoría y ubicación— y la
**Salida**. Con errores, las dos pestañas del código intermedio quedan vacías.

| Atajo | Acción |
|---|---|
| `F5` | Compilar |
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

Al final imprime dos **matrices de cobertura**: las 29 reglas semánticas y las
24 construcciones que se traducen a TAC. **Una regla o construcción sin cubrir
hace fallar la batería** igual que una prueba en rojo:

```
Cobertura de las 29 reglas semanticas:
  T1  [ok |err]  aritmetica (+,-,*,/) sobre integer o float
  ...
Cobertura de la generacion de codigo intermedio (24 construcciones):
  I01 [tac]  aritmetica (+,-,*,/,%) con precedencia y asociatividad
  ...
  casos fallidos: 16 programa(s) con errores, ninguno genero codigo intermedio
  todas las construcciones tienen su caso
```

## Con Docker

```bash
docker compose up -d --build
docker compose exec compiscript ./mvnw clean verify
docker compose exec compiscript java -jar target/compiscript.jar --test tests
docker compose exec compiscript java -jar target/compiscript.jar examples/ok_basico.cps --dot
docker compose exec compiscript java -jar target/compiscript.jar tests/tac/ok_06_herencia.cps --tac
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
  compiler/                 CompilerService: todas las fases en un solo lugar
  ide/                      IDE Swing
  tools/                    exportador DOT, arnés de pruebas y catálogos de cobertura
examples/                   programas .cps de ejemplo
tests/                      batería semántica
tests/tac/                  batería de código intermedio (.cps y su .tac esperado)
```

Los archivos que ANTLR genera (`CompiscriptLexer.java`, `CompiscriptParser.java`,
`CompiscriptVisitor.java`, `CompiscriptBaseVisitor.java`) viven en `target/` y no
se versionan: los regenera `mvnw package` a partir de la gramática.

## Nota sobre Docker

El CLI y la batería corren dentro del contenedor. El **IDE no**: la imagen no
trae servidor gráfico, así que se demuestra en el anfitrión.
