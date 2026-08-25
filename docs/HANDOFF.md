# Handoff — Compiscript Proyecto 2

> Este archivo vive fuera de los dos repos a propósito. No se versiona.

## Dónde está todo

| | |
|---|---|
| Repo | `UVG-Work/Compiscript-Proyecto2` (privado) |
| Local | `C:\Users\lordk\OneDrive\Documents\U\Compis\Compiscript-Proyecto2` |
| Rama | `main`, 5 commits, todo pusheado |
| Enunciado y gramática original | `C:\Users\lordk\OneDrive\Documents\U\Compis\Actividad-ANTLR\` (repo de la actividad previa, **no se toca**) |

`Actividad-ANTLR` es una entrega cerrada de otra actividad. Su `workspace/typecheck/`
sirve de referencia: es una maqueta en miniatura de lo que hay que construir
(Type / SymbolTable / Visitor<Type> / Main con exit code / tests).

## Qué es el proyecto

Proyecto 2 de Construcción de Compiladores (UVG). Analizador semántico para
Compiscript (subconjunto de TypeScript) en Java 17 + ANTLR 4.13.2.
Rúbrica: IDE 15 pts / analizador sintáctico y semántico 60 pts / tabla de símbolos 25 pts.

Entregables del enunciado: repo con **commits individuales** por integrante (prohibido
compartir commits), batería de pruebas con casos exitosos y fallidos por cada regla
semántica, documentación de arquitectura y de cómo ejecutar, e IDE funcional.

Equipo de 4: Andrés Mazariegos, Fernando Mendoza, Jorge Palacios, June Herrera
(el enunciado pedía 3; el catedrático autorizó 4).

## Estado actual — funciona y está verificado

Corte vertical completo: compila, parsea archivos `.cps`, reporta errores de sintaxis
con línea y columna, e imprime tokens y árbol de parseo.

```bash
mvnw.cmd clean package
java -jar target/compiscript.jar examples/ok_basico.cps --tree     # exit 0
java -jar target/compiscript.jar examples/error_sintaxis.cps       # exit 1
```

Verificado en los 4 caminos del CLI (válido, error de sintaxis, archivo inexistente,
sin argumentos) y con las 3 rutas de build: `mvn`, `./mvnw` y dentro de Docker.

Hecho:

- `pom.xml` con `antlr4-maven-plugin` (visitor sí, listener no). El lexer, el parser y
  el `CompiscriptBaseVisitor` se generan en cada compilación y **no se versionan**.
- Gramática oficial `Compiscript.g4` **sin modificar**, en
  `src/main/antlr4/com/uvg/compiscript/parser/` (el plugin deriva el paquete de la ruta).
- `Main.java` — CLI con banderas `--tree` y `--tokens`, exit 0/1/2.
- `SyntaxErrorListener.java` — acumula errores en una lista en vez de escribirlos a
  stderr. El IDE debe consumir esta misma lista para su tabla de errores.
- `types/` completo: `Type`, `PrimitiveType`, `ArrayType`, `ClassType`, `FunctionType`,
  `ErrorType`.
- `symbols/` completo: `Symbol`, `VariableSymbol`, `ParameterSymbol`, `FunctionSymbol`,
  `ClassSymbol`, `Scope`, `ScopeManager`.
- `README.md`, `Dockerfile`, `docker-compose.yml`, Maven wrapper.

Falta (todo lo demás):

- `semantic/` — no existe todavía. `DeclarationCollector` (pasada 1) y `TypeChecker`
  (pasada 2), más `ErrorReporter` y `ControlFlowContext`.
- IDE (Swing + RSyntaxTextArea + `TreeViewer` de ANTLR).
- Batería de pruebas: una prueba `.cps` por cada regla del enunciado, caso exitoso y
  caso fallido. Son 26 reglas en 7 categorías (tipos 6, ámbito 5, funciones 5,
  control de flujo 3, clases 3, listas 2, generales 3).
- Agregar `float` a la gramática (ver abajo).

El CLI imprime literalmente `analisis semantico: pendiente` donde va el recorrido.

## Decisiones de arquitectura, ya tomadas

- **Visitor, no Listener.** El tipo es atributo sintetizado (sube de hijos a padre),
  así que cada `visitXxx()` devuelve `Type`. Además hace falta controlar el orden:
  declarar los parámetros antes de entrar al cuerpo, cosa que un Listener no permite.
- **Dos pasadas.** La 1 registra solo firmas de clases y funciones sin entrar a los
  cuerpos; la 2 verifica. Sin esto, la recursión y los métodos que se llaman entre sí
  dan falsos "no declarado". La pasada 1 va en tres etapas: cascarones de clase →
  resolver herencia → firmas y miembros.
- **`ErrorType` propagable.** Cualquier operación que lo toque devuelve `ErrorType` en
  silencio, para que `let x: integer = "a" * 3;` reporte 1 error y no 3.
- **Los ámbitos no se destruyen en `pop()`**: quedan colgados del padre. La rúbrica pide
  "estado de la tabla de símbolos por cada entorno" y sin el árbol completo no hay qué
  mostrar. Ya está implementado así en `Scope`.
- **`offset`/`size` desde ya**, porque el Proyecto 3 es generación de código intermedio.
  Ya está: `Scope.define()` asigna el offset y acumula el tamaño.
- **`visitLeftHandSide` como fold**: evaluar el `primaryAtom` y aplicar cada `suffixOp`
  (call / index / property) sobre el tipo acumulado. Resuelve `obj.metodo(a)[0].campo` solo.

## Trampas de la gramática — ya detectadas, no perder tiempo redescubriéndolas

1. **`Literal` engulle a `IntegerLiteral` y `StringLiteral`.** Está declarada primero y
   gana el empate, así que esos dos tokens nunca disparan por su cuenta: el parser solo
   ve `Literal`. Confirmado con `--tokens`: el `10` sale como `Literal`.
   El `TypeChecker` determina cuál es inspeccionando `getText()` — empieza con comilla
   doble es string, tiene punto es float, si no integer. Se prefiere esto a
   reestructurar la regla: cero desviación de la gramática oficial.

2. **Las asignaciones llegan por dos caminos.** `statement` tiene `assignment`, pero
   `expressionStatement: expression ';'` también acepta `AssignExpr` y
   `PropertyAssignExpr`. ANTLR resuelve por orden de alternativas sin quejarse. El
   visitor **debe** manejar ambos nodos o se escapan asignaciones sin verificar.

3. **`float` no está en la gramática** pero el enunciado lo exige ("operandos de tipo
   integer o float"). Debe ser el **único** cambio a la gramática oficial, en commit propio:

   ```antlr
   baseType : 'boolean' | 'integer' | 'float' | 'string' | Identifier;
   Literal  : IntegerLiteral | FloatLiteral | StringLiteral;
   FloatLiteral : [0-9]+ '.' [0-9]+;
   ```

   `PrimitiveType.isAssignableFrom` ya implementa que integer promueve a float.

4. **El enunciado se contradice con `switch`.** Pide condición `boolean` para
   `if/while/do-while/for/switch`, pero el ejemplo de `docs/Especificaciones.md` usa
   `switch (x)` con enteros. Decisión: aceptar integer/string/boolean como discriminante
   y emitir **advertencia** (no error) cuando no es boolean. **Pendiente confirmarlo con
   el catedrático.**

Otras convenciones acordadas: `"texto" + 5` es error sin coerción; el `err` del `catch`
es string; `null` solo asignable a clases y arreglos; arreglos invariantes (`Perro[]` no
es `Animal[]`); shadowing permitido pero redeclaración en el mismo ámbito no; código
muerto es advertencia y falta de `return` es error; solo funciones y clases se adelantan,
las variables no.

## Entorno de la máquina (agosto 2026)

- JDK: Temurin **25** en `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot`.
  `JAVA_HOME` ya seteado a nivel de usuario.
- Maven **3.9.16** en `C:\Users\lordk\tools\apache-maven-3.9.16`, con el `bin` en el PATH
  de usuario. **Maven no está en winget** — fue descarga manual del zip de Apache.
- Se compila con `maven.compiler.release=17` porque la imagen Docker trae JDK 17.
  ANTLR 4.13.2 corre sin problema sobre JDK 25.
- El repo incluye el Maven wrapper (script-only, sin jar), así que los demás integrantes
  solo necesitan un JDK 17+ y `JAVA_HOME`.
- `gh` autenticado como `skemono`, con acceso a la org `UVG-Work`.
- `git config user.email` del repo es `her231038@uvg.edu.gt`. Cada integrante debe
  commitear con su propia cuenta o el historial no evidencia contribución individual.

## Preferencias de trabajo del usuario

- **Comentarios mínimos en el código.** Solo donde no se explica solo. Nada de cabeceras
  de bloque largas, listas de autores ni comentarios sobre cada método. Los getters,
  setters y constructores van sin comentario.
- **El reparto de tareas entre integrantes NO va en archivos del repo.** Se habla en
  conversación. Por eso este handoff está fuera de los repos.
- **Sin `Co-Authored-By: Claude`** en los commits. Ya se reescribieron los 5 para
  quitarlo; no volver a agregarlo.

## Reparto propuesto (contexto, no va a ningún archivo del repo)

Cada quien en su rama `feat/<área>`, merge a `main` por PR.

- Gramática + build + CLI: `Compiscript.g4` con float, `pom.xml`, `Main`, README,
  documentación de arquitectura.
- IDE (15 pts): Swing + RSyntaxTextArea, árbol visual con `TreeViewer`, panel de símbolos
  como `JTree` sobre `ScopeManager.getRoot()`, tabla de errores que salta a la línea.
- Tabla de símbolos (25 pts): `DeclarationCollector`, pasada 1.
- Semántica (60 pts): `TypeChecker`, `TypeRules`, `ErrorReporter`, `ControlFlowContext`.

La batería de pruebas se reparte entre los dos que terminen antes.

## Siguiente paso concreto

Que los 4 revisen juntos la API de `types/` y `symbols/` antes de separarse. Una vez que
esas firmas se congelan, las cuatro áreas avanzan en paralelo sin bloquearse.

Para el IDE hay un detalle a verificar: `org.antlr.v4.gui.TreeViewer` puede no venir en
`antlr4-runtime`. Si no está, agregar la dependencia `org.antlr:antlr4:4.13.2` al `pom.xml`.
