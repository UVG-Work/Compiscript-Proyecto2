# Compiscript — Analizador Semántico

Proyecto 2 de Construcción de Compiladores (UVG). Analizador semántico para
Compiscript, un subconjunto de TypeScript, construido sobre ANTLR 4 y Java 17.

La especificación del lenguaje está en [`docs/Especificaciones.md`](docs/Especificaciones.md)
y las decisiones de diseño en [`docs/Arquitectura.md`](docs/Arquitectura.md).

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
| `--quiet` | no imprime la tabla de símbolos |
| `--test <dir>` | corre la batería de pruebas y la matriz de cobertura |
| `--ide [archivo]` | abre el IDE gráfico |

Códigos de salida: `0` sin errores, `1` con errores, `2` uso inválido. Los tres
los distinguen el arnés de pruebas y cualquier CI.

Por defecto imprime la tabla de símbolos entorno por entorno, con el tipo de
ámbito, la profundidad, el tamaño del marco y, por símbolo, su clase, tipo,
línea, offset y capturas:

```
$ java -jar target/compiscript.jar tests/ok_03_funciones.cps
[FUNCTION] funcion crearContador (profundidad 1, 12 bytes)
  function siguiente      () => integer          linea 30   offset 0  captura [cuenta]
  var      cuenta         integer                linea 28   offset 8  capturada
```

Y los diagnósticos van con línea, columna, categoría y severidad:

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
símbolos con todos los atributos) y **Tokens**. Abajo, la tabla de **Errores**
—sintaxis y semántica juntas, con severidad, categoría y ubicación— y la
**Salida**.

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

Al final imprime la **matriz de cobertura** de las 27 reglas semánticas del
enunciado, y **una regla sin cubrir hace fallar la batería** igual que una prueba
en rojo:

```
Cobertura de las 27 reglas semanticas del enunciado:
  T1  [ok |err]  aritmetica (+,-,*,/) sobre integer o float
  ...
  A5  [ok |n/a]  un entorno nuevo por cada funcion, clase y bloque   (regla de capacidad)
  todas las reglas tienen los casos que el enunciado pide
```

## Con Docker

```bash
docker compose up -d --build
docker compose exec compiscript ./mvnw clean verify
docker compose exec compiscript java -jar target/compiscript.jar --test tests
docker compose exec compiscript java -jar target/compiscript.jar examples/ok_basico.cps --dot
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
  symbols/                  tabla de símbolos y ámbitos
  semantic/                 analizador: SemanticVisitor, TypeRules, ErrorReporter
  compiler/                 CompilerService: las cuatro fases en un solo lugar
  ide/                      IDE Swing
  tools/                    exportador DOT, arnés de pruebas y catálogo de reglas
examples/                   programas .cps de ejemplo
tests/                      batería de pruebas
```

Los archivos que ANTLR genera (`CompiscriptLexer.java`, `CompiscriptParser.java`,
`CompiscriptVisitor.java`, `CompiscriptBaseVisitor.java`) viven en `target/` y no
se versionan: los regenera `mvnw package` a partir de la gramática.

## Nota sobre Docker

El CLI y la batería corren dentro del contenedor. El **IDE no**: la imagen no
trae servidor gráfico, así que se demuestra en el anfitrión.
