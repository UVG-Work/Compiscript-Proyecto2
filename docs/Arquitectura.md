# Arquitectura y decisiones de diseño

Cada decisión aquí tiene una alternativa obvia que resultó peor. Se documenta el
porqué para no volver a discutirlas.

## Recorrido: Visitor, no Listener

El tipo de una expresión es un atributo **sintetizado**: sube de los hijos al
padre. Un `Visitor<Type>` devuelve ese tipo directamente; un Listener no devuelve
nada y obligaría a llevarlo en un `ParseTreeProperty` lateral. Además hace falta
controlar el orden — declarar los parámetros antes de entrar al cuerpo — y un
Listener no lo permite.

La gramática se generó con `visitor=true, listener=false` en el `pom.xml`.

## `ErrorType` es asignable a todo

Es la pieza que evita las cascadas. Si `let e: integer = a + b;` ya reportó
*"no se puede aplicar '+' a integer y string"*, la expresión vale `<error>` y la
inicialización **no** vuelve a reportar *"no se puede inicializar e de tipo
integer con \<error\>"*. Un error real produce un mensaje, no tres. Sin esto un
archivo con 8 errores reporta 30.

Toda la política vive en `semantic/TypeRules`: `assignable`, `comparable` y
`unify` empiezan devolviendo `true` (o el otro tipo) cuando ven un `ErrorType`.

## Adelanto de declaraciones (*hoisting*), no un barrido lineal

Un recorrido en orden textual no funciona: `factorial` se llama a sí misma antes
de terminar de declararse, `par` llama a `impar` que está más abajo, y un
atributo puede ser de una clase declarada después. Todo eso sería
"no declarado".

Por eso **cada** lista de sentencias — el programa, un bloque, el cuerpo de una
función — se recorre en dos tiempos: primero `hoist()`, después la verificación.
Como corre en cada lista, las funciones anidadas también quedan visibles antes de
usarse.

`hoist()` hace tres sub-pasos y **el orden importa**:

1. **Cascarones de clase** — solo el nombre. Así el paso 2 resuelve
   `class Perro : Animal` y los atributos de tipo `Animal` sin importar el orden
   textual.
2. **Herencia y miembros** — enlazar la superclase (detectando ciclos con liebre
   y tortuga) y construir los símbolos de atributos y métodos. Separado del paso
   1 porque resolver el tipo de un atributo exige que existan los cascarones de
   **todas** las clases.
3. **Firmas de funciones libres** — al final, porque sus parámetros pueden ser de
   tipo clase.

Consecuencia: `visitFunctionDeclaration` **no** define el símbolo, solo lo busca y
verifica el cuerpo. La detección de funciones duplicadas ocurre en `hoist`.

## La tabla de símbolos es un registro, no una pila que se vacía

`ScopeManager.pop()` cierra el ámbito para la resolución de nombres pero **no lo
destruye**: queda colgado de su padre en `Scope.children`. El enunciado pide
imprimir "el estado de la tabla de símbolos por cada entorno", y eso solo se
puede si los ámbitos sobreviven al recorrido. Las fases posteriores también los
necesitan.

`Scope` usa `LinkedHashMap` y no `HashMap` para imprimir en orden de declaración.
`define()` asigna `scopeDepth` y `offset` acumulando anchos, porque el Proyecto 3
es generación de código intermedio y los offsets alimentan el marco de pila.

## Detección de closures

`Scope.enclosingFunction()` devuelve el ámbito de función que contiene a este. Si
un nombre se resuelve en un ámbito cuya función dueña **no es** la función
actual, la función actual lo está capturando:

```java
Scope ownerFunction = scopes.current().scopeOf(name).enclosingFunction();
if (ownerFunction != null && ownerFunction != currentFunction) { /* captura */ }
```

La condición `!= null` excluye los globales: usar una variable global **no** es
capturar. `scopeOf(name)` hace falta porque `resolve(name)` devuelve el símbolo
pero no *dónde* se encontró.

## `alwaysReturns` es conservador a propósito

Solo cuenta un `return`, un bloque que retorna, o un `if/else` donde retornan las
**dos** ramas. Un `while (true)` con `return` adentro no cuenta. Ser conservador
produce falsos positivos en código raro; ser optimista deja pasar funciones que de
verdad no retornan. El falso positivo es el error correcto aquí.

## Trampas de la gramática

Estas se descubrieron leyendo el parser generado, no adivinando.

1. **El token `Literal` engulle a `FloatLiteral`, `IntegerLiteral` y
   `StringLiteral`.** Está declarado antes y gana el empate, así que los tres
   nunca disparan por su cuenta: el parser sólo ve `Literal`. `visitLiteralExpr`
   los distingue por el texto — empieza con comilla doble es `string`, tiene punto
   es `float`, si no es `integer`. Se prefiere esto a reestructurar la regla.

   `FloatLiteral` va **primero** dentro de `Literal` por claridad, aunque ANTLR
   ya resolvería `3.25` por máxima longitud. Y `a.length` sigue partiéndose bien
   en `a` `.` `length`, porque `FloatLiteral` exige dígitos a ambos lados del
   punto; se verificó con `--tokens`.

2. **La gramática es ambigua en `statement`, y ANTLR lo resuelve solo.** Con
   `PredictionMode.LL_EXACT_AMBIG_DETECTION` y un `DiagnosticErrorListener`:

   ```
   reportAmbiguity d=1 (statement): ambigAlts={3, 6}, input='x=1;'
   reportAmbiguity d=1 (statement): ambigAlts={3, 6}, input='obj.campo=2;'
   ```

   Las alternativas 3 y 6 son `assignment` y `expressionStatement`: `x = 1;`
   deriva por las dos. ANTLR toma la de menor número (`assignment`), así que el
   analizador funciona sin tocar la gramática. Aun así el visitor implementa
   `visitAssignExpr` y `visitPropertyAssignExpr`, porque esa forma sí aparece
   dentro de otras expresiones.

   Un generador LALR(1) daría conflicto *reduce/reduce* aquí. Es el argumento
   técnico decisivo si alguien propone migrar fuera de ANTLR.

   Al reproducirlo, **no** llamar a `removeErrorListeners()` antes de agregar el
   `DiagnosticErrorListener`: silencia el diagnóstico y parece que no hay
   ambigüedad.

3. **La cabecera del `for` es ambigua y hay que desambiguarla a mano.**

   ```antlr
   forStatement: 'for' '(' (variableDeclaration | assignment | ';') expression? ';' expression? ')' block;
   ```

   `ctx.expression()` es una lista y el accesor no dice si el único elemento
   presente es la condición o el incremento. Se decide por la posición respecto
   al `;` separador: si el inicializador es una regla, esa regla **se come su
   propio `;`** y no es hijo directo, así que el primer `;` hijo directo es el
   separador. Si el inicializador es el `;` vacío, ese sí es hijo directo y hay
   que saltarlo.

4. **Los operadores de las reglas binarias no tienen etiqueta**, así que no hay
   accesor: se sacan por posición con `ctx.getChild(2*i-1)`.

5. **`if (parts.size() == 1) return visit(parts.get(0));`** va en las seis reglas
   binarias. Sin eso el tipo no sube por la cadena de precedencia y todo se
   vuelve `boolean` o `<error>`.

6. **No llamar a `visit(ctx.block())` desde el cuerpo de una función**:
   `visitBlock` abre un ámbito nuevo y los parámetros ya viven en el ámbito de la
   función. Se llama directo a `visitStatementList(ctx.block().statement())` para
   no crear un ámbito de más. Lo mismo en `foreach` y en el `catch`, donde la
   variable de control vive en el mismo ámbito que el cuerpo.

## `foldSuffixes`: por qué no basta `visitLeftHandSide`

`leftHandSide : primaryAtom (suffixOp)*` cubre `obj`, `obj.campo`, `f(x)`,
`m[0][1]` y `obj.metodo(a).campo`. Se pliega de izquierda a derecha: la llamada
exige `FunctionType` y valida argumentos, el índice exige `ArrayType` e índice
`integer`, y la propiedad exige `ClassType` y busca el miembro en la cadena de
herencia.

Pero para `obj.campo = 5` hace falta el tipo **del objeto**, o sea el resultado de
aplicar todos los sufijos **menos el último**. Por eso la función recibe cuántos
aplicar. Sin esa separación, la asignación a propiedad pierde la verificación de
`const` y de existencia del miembro.

## Validación de índices fuera de rango

Se usa `VariableSymbol.knownLength`: se llena cuando la variable se inicializa con
un literal de arreglo y se invalida (`-1`) en cualquier reasignación. Es análisis
de flujo lineal, **no vale dentro de bucles**; detecta el caso del enunciado
(`validas[5]` sobre una lista de 3) y nada más. Limitación conocida y aceptada.

## Árbol visual: DOT, no `grun -gui`

`grun -gui` abre una ventana Swing y no funciona dentro del contenedor, que no
tiene entorno gráfico. `Trees.toStringTree()` da texto, que no alcanza para
"representación visual".

`tools/TreeExporter` emite DOT recorriendo el árbol — nodos de regla en elipse
azul, hojas de token en caja ámbar — y renderiza a PNG invocando `dot` si está
disponible. El `Dockerfile` instala Graphviz para que funcione dentro del
contenedor. Hay que escapar comillas, backslashes y saltos de línea o DOT no
compila.

## Errores de sintaxis: recolectarlos, no dejarlos en stderr

ANTLR los imprime por su cuenta con otro formato. `SyntaxErrorListener` los guarda
en una lista con el mismo formato que los semánticos, y el IDE consumirá esa misma
lista.

**Si hay errores de sintaxis no se corre el análisis semántico**: el árbol está
mal formado y produciría errores fantasma.

## Dónde el enunciado y la gramática se contradicen

El criterio fue: la gramática oficial manda, salvo que el propio enunciado se
contradiga con su ejemplo.

| Tema | Decisión | Por qué |
|---|---|---|
| `float` | **Añadido a la gramática** | El enunciado es explícito: los operandos aritméticos son "de tipo `integer` o `float`". Es la **única** modificación a la gramática oficial: `baseType` gana `'float'` y el lexer gana `FloatLiteral`. `integer` promueve a `float`, nunca al revés, y `%` sigue aceptando solo `integer`. |
| Selector de `switch` | **Debe ser `boolean`**, igual que `if`/`while` | El enunciado lo dice literalmente: "Las condiciones de `if`, `while`, `do-while`, `for` y `switch` deben ser de tipo `boolean`". Se sigue su letra. **Consecuencia asumida:** el ejemplo de `switch (x)` con `case 1:` de [`Especificaciones.md`](Especificaciones.md) queda inválido — el enunciado se contradice consigo mismo y se eligió su regla explícita sobre su ejemplo. |
| `case` de un `switch` | Debe ser comparable con el selector | Es una regla **distinta** de la anterior y se verifica aparte: un `switch` con selector no boolean **y** un `case` incomparable produce dos errores, no uno. |
| `break` en `switch` | Error si no hay bucle alrededor | El enunciado dice "solo dentro de bucles" y su ejemplo de `switch` no usa `break`. Se sigue al pie de la letra. |
| Tipo de los atributos | Obligatorio | Un método puede usar un atributo antes de que aparezca textualmente; inferirlo exigiría una tercera pasada. |
| `.length` en listas | Añadido | Única propiedad predefinida; sin ella no se puede recorrer una lista con `for`, solo con `foreach`. Es la única extensión al lenguaje. |
| `[]` y `null` | `[]` encaja en cualquier lista; `null` en cualquier tipo por referencia (clases y arreglos) | Sin esto, `let vacia: integer[] = [];` y `let d = null;` — ambos del enunciado — serían errores. |
| Arreglos | Invariantes: `Perro[]` no cabe en `Animal[]` | Escribir un `Gato` en el destino corrompería el origen. |
| `"texto" + 5` | Error, sin coerción | `+` concatena `string + string` y suma `integer + integer`; nada más. |
| Inicialización de `const` | Ya la obliga la gramática | El `=` es obligatorio sintácticamente, así que la regla se cumple sin código. `tests/err_08_sintaxis.cps` lo demuestra. Lo que sí se implementa es que no se reasigne. |
| Código muerto | Error, un mensaje por tramo | El flag `unreachable` se reinicia tras reportar, para no dar un mensaje por cada sentencia sobrante. |
| Declarada y no usada | Advertencia, no error | No impide compilar; no cuenta para el código de salida. |

## Las 27 reglas del enunciado y su cobertura

El enunciado pide una batería "que valide casos exitosos y fallidos **para cada
regla semántica**". Las reglas se numeraron en
[`tools/RuleCatalog.java`](../src/main/java/com/uvg/compiscript/tools/RuleCatalog.java)
y cada `.cps` de [`tests/`](../tests) declara cuáles ejercita:

```
// @esperado: ERRORES 8
// @regla: T1, T2, T3, T4, T5, C1
```

`TestRunner` acumula esas etiquetas e imprime una matriz de cobertura al final;
**una regla sin cubrir hace fallar la batería**, igual que una prueba en rojo. Así
"tenemos pruebas" pasa a ser "demostramos cobertura de las 27 reglas".

| Grupo | Reglas |
|---|---|
| Sistema de tipos | `T1` aritmética · `T2` lógicas · `T3` comparaciones · `T4` asignaciones · `T5` `const` · `T6` listas y estructuras |
| Manejo de ámbito | `A1` resolución de nombres · `A2` variables no declaradas · `A3` redeclaración · `A4` bloques anidados · `A5` un entorno por función/clase/bloque |
| Funciones | `F1` argumentos · `F2` retorno · `F3` recursión · `F4` anidadas y closures · `F5` redeclaración |
| Control de flujo | `C1` condiciones `boolean` · `C2` `break`/`continue` · `C3` `return` |
| Clases | `K1` atributos y métodos · `K2` constructor · `K3` `this` |
| Listas | `L1` tipo de los elementos · `L2` índices |
| Generales | `G1` código muerto · `G2` sentido semántico · `G3` declaraciones duplicadas |

**`A5`, `F3` y `F4` son reglas de capacidad**: describen algo que el lenguaje debe
soportar (crear un entorno, permitir recursión, permitir closures), no una
restricción que un programa pueda violar. Pedirles un "caso fallido" no tiene
sentido, así que la matriz las marca `n/a` y sólo exige el caso exitoso. Están
declaradas en `RuleCatalog.CAPABILITY_ONLY`.

## El IDE

Paquete [`ide/`](../src/main/java/com/uvg/compiscript/ide), Swing. Se abre con
`java -jar target/compiscript.jar --ide`, en el mismo jar que el CLI: un solo
artefacto y ningún comando extra que memorizar.

| Clase | Rol |
|---|---|
| `IdeFrame` | Editor a la izquierda; pestañas Árbol / Tabla de símbolos / Tokens a la derecha; Errores y Salida abajo. `F5` compila, `F6` corre la batería |
| `EditorPanel` | `RSyntaxTextArea` con números de línea. Pinta de rojo las líneas con error y de ámbar las que tienen advertencia |
| `TreePanel` | `org.antlr.v4.gui.TreeViewer` con zoom, más exportación a DOT/PNG reutilizando `tools/TreeExporter` |
| `SymbolTablePanel` | `JTree` de ámbitos; al seleccionar uno, una tabla con **todos** los atributos de sus símbolos. Es la salida "estado de la tabla de símbolos por cada entorno" |
| `DiagnosticsPanel` | Tabla Severidad / Categoría / Línea / Columna / Mensaje. Doble clic salta a la línea |
| `TokensPanel` | El flujo de tokens del lexer |

**`compiler/CompilerService` es la pieza clave.** Antes, `Main.analyze` imprimía a
`System.out` y no devolvía nada reutilizable; el IDE habría tenido que duplicar el
cableado lexer→parser→listener→analizador, como ya lo duplicaba `TestRunner`. Ahora
los tres consumen el mismo servicio, que devuelve un `CompilationResult` con árbol,
tokens, ámbito global y diagnósticos.

Los errores de sintaxis se convierten a `SemanticError` con categoría `SINTAXIS` y
viajan **en la misma lista** que los semánticos: el enunciado pide reportarlos "con
su tipo y ubicación" y una sola tabla es lo que el IDE necesita.

**Por qué `TreeViewer` de ANTLR y no un `JTree`:** dibuja el diagrama de nodos y
aristas, que es lo que se espera al leer "representación visual del árbol". Vive en
el artefacto `org.antlr:antlr4` (no en `antlr4-runtime`), y se comprobó leyendo el
bytecode que **ninguna** clase de `org/antlr/v4/gui` referencia `icu4j`: sólo
`javax.swing` y `org.abego.treelayout`. Por eso el `pom.xml` lo trae con
`icu4j` y `mojo-executor` excluidos, y el jar sombreado queda en ~3,6 MB en vez de
~17 MB.

**Por qué el resaltador de JavaScript:** Compiscript comparte con JavaScript las
palabras clave (`let`, `const`, `class`, `function`, `if`, `while`, `return`), las
cadenas con comilla doble y los comentarios `//` y `/* */`. Reutilizarlo es una
línea; escribir un `TokenMaker` propio sería trabajo sin ganancia visible.

## Correspondencia con el material del curso

| Diapositiva | Dónde aparece en el código |
|---|---|
| **05**, "pasos para crear un sistema de tipos": definir tipos → reglas de tipos → reglas de ámbito | `types/` → `semantic/TypeRules` → `symbols/Scope` |
| **05**, reglas como juicios `Γ ⊢ M : A` | `TypeRules.assignable/comparable/unify`: las premisas son los tipos de los hijos y la conclusión el tipo que se devuelve al padre |
| **05**, `int + int : int`, `int + float : float`, `int + char : error` | `SemanticVisitor.additive`, incluida la promoción a `float` y el sumidero `ErrorType` |
| **05**, lámina 30 ("¿pero cómo lo programo?"): anotar el árbol de abajo hacia arriba | Es literalmente el `Visitor<Type>`: cada `visitXxx` devuelve el tipo que sube |
| **02**, el tipo es un **atributo sintetizado** | El argumento formal de por qué Visitor y no Listener: un Listener no devuelve valores y obligaría a un `ParseTreeProperty` lateral |
| **03**, `int[2][3] → array(2, array(3, int))` | `ArrayType(ArrayType(INTEGER))`, con `equals` estructural |
| **04**, lámina 7, atributos mínimos | `Symbol`: lexema, token, tipo de dato, alcance, posición de memoria, línea y columna, tipo semántico; más número, tipo y **método de paso** de parámetros en `FunctionSymbol`/`ParameterSymbol` |
| **04**, lámina 8, tres operaciones básicas | Insertar `Scope.define`, recuperar `Scope.resolve`/`resolveLocal`, actualizar los mutadores de `Symbol` (`setType`, `setInitialized`, `setKnownLength`, `setCaptured`), que el visitor usa durante todo el recorrido |
| **04**, lámina 10, "tabla de símbolos por alcance" | El árbol de `Scope`, que sobrevive al recorrido y es lo que pinta el IDE |

Sobre la tercera operación: **no** se añadió un `Scope.update(...)` aparte. La
actualización ya ocurre sobre el objeto `Symbol` recuperado, que es lo que hace el
visitor cada vez que marca una variable como inicializada, capturada, o invalida su
longitud conocida. Un método extra que nadie llamara sería código muerto — y
justamente detectarlo es una de las reglas de esta entrega.

## Errores a no repetir

1. **Los terminadores consecutivos se marcan como código muerto entre sí.** Un
   caso de prueba con `break; continue; return;` seguidos esperaba 3 errores y da
   6, porque cada uno marca al siguiente como inalcanzable. Es el comportamiento
   correcto, pero contamina una prueba que quería medir otra cosa. Se aísla cada
   uno en su propio bloque: `{ break; } { continue; }`. **Cada caso debe probar
   una regla**; si dispara reglas de otra categoría, se reestructura el caso, no
   se relaja el analizador.

2. **No inventar el número esperado de errores.** Correr el caso, leer la salida,
   verificar que cada mensaje sea legítimo, y recién entonces fijar el número en
   la cabecera.
