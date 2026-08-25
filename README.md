# Compiscript — Analizador Semántico

Proyecto 2 de Construcción de Compiladores (UVG). Analizador semántico para
Compiscript, un subconjunto de TypeScript, construido sobre ANTLR 4 y Java 17.

La especificación del lenguaje está en [`docs/Especificaciones.md`](docs/Especificaciones.md).

## Requisitos

- JDK 17 o superior
- Maven (o usar el wrapper `./mvnw` incluido, que lo descarga solo)

## Compilar

```bash
./mvnw clean package        # Linux / macOS
mvnw.cmd clean package      # Windows
```

Genera `target/compiscript.jar`, con el runtime de ANTLR incluido.

## Ejecutar

```bash
java -jar target/compiscript.jar examples/ok_basico.cps
```

| Bandera | Efecto |
|---------|--------|
| `--tree` | imprime el árbol de parseo |
| `--tokens` | imprime el flujo de tokens |

Termina con código de salida `0` si no hubo errores y `1` si hubo alguno.

```
$ java -jar target/compiscript.jar examples/error_sintaxis.cps
=== examples/error_sintaxis.cps ===
Errores de sintaxis (1):
  linea 5:10 missing ')' at '{'
RESULTADO: FALLO
```

## Con Docker

Para correrlo sin instalar Java ni Maven:

```bash
docker compose up -d --build
docker compose exec compiscript ./mvnw clean package
docker compose exec compiscript java -jar target/compiscript.jar examples/ok_basico.cps --tree
```

## Estructura

```
src/main/antlr4/com/uvg/compiscript/parser/
  Compiscript.g4          gramática oficial; ANTLR genera de aquí el lexer y el parser
src/main/java/com/uvg/compiscript/
  Main.java               driver de línea de comandos
  SyntaxErrorListener.java  acumula los errores de sintaxis en vez de escribirlos a stderr
  types/                  sistema de tipos
  symbols/                tabla de símbolos y ámbitos
examples/                 programas .cps de ejemplo
```

Los archivos que ANTLR genera (`CompiscriptLexer.java`, `CompiscriptParser.java`,
`CompiscriptBaseVisitor.java`) viven en `target/` y no se versionan: los regenera
`mvnw package` a partir de la gramática.
