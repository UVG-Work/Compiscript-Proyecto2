# Código intermedio de Compiscript

Este documento define el lenguaje intermedio que produce el compilador: un
código de tres direcciones (TAC). Cubre los operandos, las instrucciones, el
modelo de memoria, la convención de llamada y el manejo de temporales, con un
ejemplo de traducción por construcción y la lista de supuestos.

Todos los ejemplos están copiados de la salida real del compilador. El programa
completo de cada uno está en [`tests/tac/`](../tests/tac), junto a su `.tac`
esperado, y la batería verifica que la salida no cambie.

```bash
java -jar target/compiscript.jar tests/tac/ok_03_funciones.cps --tac
```

## Cuándo se genera

El código intermedio es la última fase y solo corre si el programa no tiene
errores de sintaxis ni semánticos. Con cualquier error se muestran los errores
y no se produce TAC.

```
fuente .cps → lexer/parser → análisis semántico → distribución de memoria → TAC
```

El generador no vuelve a resolver nombres ni a inferir tipos. Durante el
análisis semántico se anota en el árbol a qué símbolo apunta cada nombre y qué
tipo tiene cada expresión, y el generador solo lee esas anotaciones y las
direcciones que dejó la distribución de memoria.

## Forma de un programa

Un programa es una lista de funciones. El código global va en una función
implícita `main`; después siguen las funciones del fuente, en el orden en que
aparecen. Si hay clases, sus tablas de métodos van al principio.

```
vtable Punto.vtable: Punto.mover, Punto.norma

func main, 48
    t1 = alloc 24                   ; let p: Punto = new Punto(3, 4);
    ...
endfunc

func Punto.mover, 60
    ...
endfunc
```

`func etiqueta, n` abre una función cuyo registro de activación ocupa `n`
bytes; `endfunc` la cierra y equivale a un `return` sin valor.

Las etiquetas se forman así: una función del nivel superior usa su nombre; una
anidada lleva delante la etiqueta de su contenedora (`crearContador.avanzar`);
un método, el nombre de su clase (`Punto.mover`). El compilador inventa tres:
`main`, `Clase.vtable` y `Clase.init`. Toda etiqueta es única. Si un nombre del
programa choca con una ya asignada (una función llamada `main`, un método
llamado `init`, dos funciones homónimas en bloques hermanos), la segunda
recibe el sufijo `$2`, `$3`… Como `$` no puede aparecer en un identificador,
el sufijo nunca vuelve a chocar. Las líneas
sangradas son instrucciones; las que terminan en `:` son etiquetas. Lo que va
después de `;` es la sentencia del fuente que originó la instrucción. Es solo
un comentario para leer el TAC junto al programa.

## Operandos

| Forma | Qué es |
|---|---|
| `t1`, `t2`, … | Temporales. La numeración reinicia en cada función. |
| `G[n]` | Variable en el área estática, a `n` bytes de su inicio. |
| `fp[n]` | Variable en el registro de activación actual, a `n` bytes de `fp`. |
| `fp` | La dirección del registro de activación actual. |
| `5`, `3.25`, `"hola"`, `true`, `false`, `null` | Constantes, escritas igual que en el fuente. |
| `factorial`, `Punto.mover`, `Punto.vtable` | Etiquetas de código o de datos. |

`G[n]` y `fp[n]` se usan como nombres de variables: pueden aparecer en
cualquier posición de cualquier instrucción. La memoria indirecta, en cambio,
solo aparece en las dos instrucciones de copia indexada, y siempre a través de
un temporal que contiene la dirección base.

## Instrucciones

| Instrucción | Significado |
|---|---|
| `x = y op z` | Operación binaria; `op` es `+ - * / % == != < <= > >=`. |
| `x = op y` | Operación unaria; `op` es `-` o `!`. |
| `x = y` | Copia. |
| `x = (float) y` | Convierte un `integer` a `float`. |
| `x = concat y, z` | Concatena dos `string`. |
| `x = t[i]` | Lee la memoria en la dirección `t + i`. |
| `t[i] = y` | Escribe `y` en la dirección `t + i`. |
| `x = alloc n` | Reserva `n` bytes en el heap y deja su dirección en `x`. |
| `goto L` | Salto incondicional. |
| `if x goto L` · `ifFalse x goto L` | Salta si `x` es verdadero, o si es falso. |
| `if y relop z goto L` · `ifFalse y relop z goto L` | Salto con una comparación. |
| `L:` | Etiqueta. |
| `param x` | Pasa un argumento a la próxima llamada. |
| `link x` | Fija el enlace de acceso de la próxima llamada. |
| `x = call f, n` · `call f, n` | Llama a `f` con los `n` últimos `param`, guardando o no el resultado. `f` puede ser una etiqueta o un temporal con la dirección del código. |
| `return x` · `return` | Retorna, con o sin valor. |
| `x = access k` | Dirección del registro de activación `k` niveles arriba en la cadena de enlaces de acceso. |
| `print x` | Imprime `x`. |
| `try L, x` · `endtry` | Protege el código hasta `endtry`. Si algo falla ahí, guarda el mensaje en `x` y salta a `L`. |
| `func f, n` · `endfunc` | Delimitan una función y el tamaño de su registro de activación. |
| `vtable C.vtable: m1, m2, …` | Datos: la tabla de métodos de la clase `C`, una dirección por ranura. |

Cada instrucción tiene a lo sumo tres direcciones. Las únicas que leen o
escriben memoria indirecta son `x = t[i]` y `t[i] = y`.

## Modelo de memoria

### Tamaños

| Tipo | Bytes |
|---|---|
| `integer` | 4 |
| `float` | 8 |
| `boolean` | 1 |
| `string` | 8 (referencia) |
| clases, listas y funciones | 8 (referencia) |

No hay alineación: cada variable empieza donde termina la anterior.

### Área estática

Las variables globales, incluidas las declaradas en bloques del nivel superior,
viven en `G` en orden de declaración. Las funciones y las clases no ocupan
espacio ahí.

### Registro de activación

Cada llamada crea un registro de activación con esta forma:

| Desplazamiento | Contenido |
|---|---|
| `fp[0]` | Enlace de control: el `fp` de quien llamó. |
| `fp[8]` | Enlace de acceso: el registro de la función que **declara** a esta. |
| `fp[16]` | Dirección de retorno. |
| `fp[24]` | Valor de retorno. |
| `fp[32]` | `this` (solo en métodos). |
| … | Parámetros, en orden. |
| … | Variables locales de todos los bloques de la función. |
| … | Temporales, 8 bytes cada uno. |

Los desplazamientos se cuentan **por función**, no por bloque. Dos bloques de
la misma función nunca comparten espacio. Así queda el registro de `factorial`
(`tests/tac/ok_03_funciones.cps`):

```
Registro de activacion de factorial (44 bytes)
  fp[0]                                            enlace de control
  fp[8]                                            enlace de acceso
  fp[16]                                           direccion de retorno
  fp[24]                                           valor de retorno
  fp[32]                 n            integer      parametro
  fp[36]                 t1                        temporal
```

### Objetos

Un objeto vive en el heap. En `+0` guarda la dirección de la tabla de métodos
de su clase; después van los atributos heredados y luego los propios. Así, un
`Perro` empieza con exactamente la misma forma que un `Animal`, y el código
escrito para `Animal` funciona sobre él:

```
Objeto Perro (32 bytes)
  +0                                               tabla de metodos Perro.vtable
  +8                     nombre       string       atributo de Animal
  +16                    sonido       string       atributo de Animal
  +24                    raza         string       atributo de Perro
  Perro.vtable[0]        hablar                    Perro.hablar
  Perro.vtable[8]        presentar                 Animal.presentar
```

Cada método ocupa una ranura de la tabla. La subclase hereda la tabla del padre
y reemplaza la ranura del método que sobrescribe, así que un mismo método
queda en la misma ranura en toda la jerarquía.

### Listas

Una lista vive en el heap. En `+0` guarda su longitud y desde `+8` sus
elementos, uno tras otro, cada uno con el tamaño de su tipo. El elemento `i`
está en `8 + i * tamaño`.

### Valores función

Una función guardada en una variable es un par de 16 bytes: `[0]` es la
etiqueta de su código y `[8]` su enlace de acceso.

## Convención de llamada

1. Quien llama evalúa **todos** los argumentos primero, convirtiéndolos al tipo
   del parámetro si hace falta. Después emite los `param` seguidos, sin código
   entre ellos, para que una llamada anidada en un argumento no se mezcle con
   los parámetros de la exterior.
2. Si la función destino es anidada, `link` fija su enlace de acceso: `link fp`
   si la declaró la función actual, o `access k` si la declaró una función más
   externa. Las funciones del nivel superior no lo necesitan: lo único que
   pueden ver fuera de sí mismas son los globales, que tienen dirección fija.
3. `call` crea el registro de activación, guarda en él el enlace de control y
   la dirección de retorno, y copia los parámetros a partir de `fp[32]`.
4. En un método, el primer `param` es el objeto receptor, que queda en
   `fp[32]` como `this`, y `n` lo cuenta.
5. `return x` deja el valor en `fp[24]`, restaura el `fp` de quien llamó desde
   el enlace de control y vuelve a la dirección de retorno.

## Temporales

Cada función tiene su propio conjunto de temporales. El algoritmo es:

- **Asignación:** se entrega siempre el temporal libre de número más bajo, y
  solo se crea uno nuevo si no hay ninguno libre.
- **Reciclaje:** quien consume un operando lo libera en cuanto emite la
  instrucción que lo usa. Una operación libera sus dos operandos **antes** de
  pedir el temporal del resultado, así que el resultado puede reutilizar uno
  de ellos.
- Solo se liberan temporales. Las variables y las constantes no pertenecen al
  conjunto.
- La lista y el índice de un `foreach` deben sobrevivir a todo el cuerpo, así
  que se liberan recién al terminar el bucle. El selector de un `switch`, en
  cambio, solo hace falta en la cadena de comparaciones: se libera antes de
  los cuerpos de los `case`, que pueden reutilizar su temporal.

Como siempre se reutiliza el menor libre, el total de temporales creados en una
función es el máximo que estuvieron vivos a la vez, y eso es lo que se reserva
en el registro de activación.

```
let c: integer = a + b * 2 - BASE / (a - b) % 4;
```

```
    t1 = G[8] * 2                   ; let c: integer = a + b * 2 - BASE / (a - b) % 4;
    t1 = G[4] + t1
    t2 = G[4] - G[8]
    t2 = G[0] / t2
    t2 = t2 % 4
    t1 = t1 - t2
    G[12] = t1
```

Seis operaciones y solo dos temporales. Todo el programa de
`ok_01_expresiones.cps` usa dos.

## Tabla de símbolos

La fase agrega a la tabla de símbolos lo que la generación necesita:

| Símbolo | Lo que se agrega |
|---|---|
| Variable o parámetro | Desplazamiento y dirección: `G[n]` o `fp[n]`. |
| Atributo | Desplazamiento dentro del objeto: `+n`. |
| Función o método | Etiqueta, tamaño del registro de activación y cantidad de temporales. |
| Clase | Tamaño de instancia, atributos en orden de memoria y tabla de métodos. |

El CLI la imprime con una columna `dir` y, debajo del TAC, el área estática,
el registro de activación de cada función y la forma de cada objeto. En el IDE
están en las pestañas **Tabla de símbolos** y **Registros de activación**.

## Ejemplos de traducción

### Expresiones y asignaciones

La precedencia y la asociatividad salen de la gramática: el recorrido emite
primero el operando que se agrupa primero, y cada resultado parcial es el
operando izquierdo de la siguiente operación.

```
let d: integer = a - b - 1;
let e: integer = -a;
```

```
    t1 = G[4] - G[8]                ; let d: integer = a - b - 1;
    t1 = t1 - 1
    G[16] = t1
    t1 = - G[4]                     ; let e: integer = -a;
    G[20] = t1
```

Una constante se guarda igual que una variable: `const` ya lo hizo cumplir el
análisis semántico.

### Operadores lógicos con cortocircuito

`&&` y `||` no son instrucciones: se traducen a saltos, y el segundo operando
solo se evalúa si el primero no decide. Usados como valor, los saltos dejan
`true` o `false` en un temporal.

```
let rango: boolean = a >= 0 && a <= 10 || !(b != 3);
```

```
    ifFalse G[4] >= 0 goto L4       ; let rango: boolean = a >= 0 && a <= 10 || !(b != 3);
    if G[4] <= 10 goto L3
L4:
    if G[8] != 3 goto L1
L3:
    t1 = true
    goto L2
L1:
    t1 = false
L2:
    G[25] = t1
```

`!` tampoco genera código en una condición: invierte el sentido del salto.

### Comparaciones e `if/else`

Una condición con una sola comparación va directo a la instrucción de salto.

```
if (total == 0) {
  total = 1;
} else {
  total = 2;
}
```

```
    ifFalse G[0] == 0 goto L1
    G[0] = 1                        ; total = 1;
    goto L2
L1:
    G[0] = 2                        ; total = 2;
L2:
```

Usada como valor, la comparación deja su resultado en un temporal:
`t1 = G[4] > G[8]`.

### `while` y `do-while`

```
while (i < 5) {
  i = i + 1;
  if (i == 2) {
    continue;
  }
  total = total + i;
}

do {
  i = i - 1;
} while (i > 0);
```

```
L3:
    ifFalse G[4] < 5 goto L4
    t1 = G[4] + 1                   ; i = i + 1;
    G[4] = t1
    ifFalse G[4] == 2 goto L5
    goto L3                         ; continue;
L5:
    t1 = G[0] + G[4]                ; total = total + i;
    G[0] = t1
    goto L3
L4:
L6:
    t1 = G[4] - 1                   ; i = i - 1;
    G[4] = t1
L7:
    if G[4] > 0 goto L6
L8:
```

El cuerpo de un `do-while` corre antes de la condición. Su `continue` saltaría
a `L7`, la condición, y su `break` a `L8`.

Una condición que es la constante `true` no emite ningún salto: `while (true)`
solo tiene el `goto` del final del cuerpo.

### `for`, `break` y `continue`

El `for` tiene tres etiquetas: la condición, el incremento y la salida.
`continue` salta al incremento y `break` a la salida. Cada bucle apila sus
etiquetas, así que un `break` dentro de un `switch` sale del `switch` y no del
bucle, y uno fuera del `switch` sale del bucle:

```
for (let j: integer = 0; j < 10; j = j + 1) {
  if (j == 3) { continue; }
  switch (j) { ... }
  if (j == 8) { break; }
}
```

```
    G[8] = 0                        ; let j: integer = 0;
L9:
    ifFalse G[8] < 10 goto L11
    ifFalse G[8] == 3 goto L12
    goto L10                        ; continue;
L12:
    ...
L16:
    ifFalse G[8] == 8 goto L17
    goto L11                        ; break;
L17:
L10:
    t1 = G[8] + 1                   ; j = j + 1
    G[8] = t1
    goto L9
L11:
```

### `switch`

El selector se evalúa una sola vez. Primero va una cadena de comparaciones que
salta al `case` correspondiente; después, los cuerpos uno tras otro. Sin
`break`, un `case` cae en el siguiente, igual que en C y en TypeScript.

```
switch (j) {
  case 1:
    total = total + 10;
    break;
  case 7:
    break;
  default:
    total = total + 1;
}
```

```
    if G[8] == 1 goto L13
    if G[8] == 7 goto L14
    goto L15
L13:
    t1 = G[0] + 10                  ; total = total + 10;
    G[0] = t1
    goto L16                        ; break;
L14:
    goto L16                        ; break;
L15:
    t1 = G[0] + 1                   ; total = total + 1;
    G[0] = t1
L16:
```

### Funciones, parámetros, retorno y recursión

```
function factorial(n: integer): integer {
  if (n <= 1) {
    return 1;
  }
  return n * factorial(n - 1);
}

print(sumar(factorial(3), 4));
```

```
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

```
    param 3                         ; print(sumar(factorial(3), 4));
    t1 = call factorial, 1
    param t1
    param 4
    t1 = call sumar, 2
    print t1
```

La recursión no necesita nada especial: cada `call` crea un registro de
activación nuevo, así que cada llamada tiene su propio `n` en `fp[32]`.

### Funciones anidadas y closures

Una función anidada lee y escribe las variables de la función que la contiene
a través del enlace de acceso. `access k` sube `k` niveles, donde `k` es la
diferencia de anidamiento entre la función actual y la dueña de la variable.

```
function crearContador(inicio: integer): integer {
  let cuenta: integer = inicio;

  function avanzar(paso: integer): integer {
    cuenta = cuenta + paso;
    return cuenta;
  }

  avanzar(1);
  return avanzar(2);
}
```

```
func crearContador, 48
    fp[36] = fp[32]                 ; let cuenta: integer = inicio;
    param 1                         ; avanzar(1);
    link fp
    t1 = call crearContador.avanzar, 1
    param 2                         ; return avanzar(2);
    link fp
    t1 = call crearContador.avanzar, 1
    return t1
endfunc

func crearContador.avanzar, 52
    t1 = access 1                   ; cuenta = cuenta + paso;
    t1 = t1[36]
    t1 = t1 + fp[32]
    t2 = access 1
    t2[36] = t1
    t1 = access 1                   ; return cuenta;
    t1 = t1[36]
    return t1
endfunc
```

Dos niveles hacia arriba es `access 2` (`tests/tac/ok_08_closures.cps`). La
etiqueta de una función anidada lleva delante la de su contenedora, así que dos
funciones internas con el mismo nombre en funciones distintas no chocan.

### Funciones como valor

Guardar una función en una variable crea el par `{código, enlace de acceso}`.
Llamarla a través de la variable lee los dos campos y hace una llamada
indirecta.

```
let f = doble;
print(f(21));
```

```
    t1 = alloc 16                   ; let f = doble;
    t1[0] = doble
    t1[8] = null
    G[0] = t1
    t1 = G[0]                       ; print(f(21));
    t2 = t1[0]
    t3 = t1[8]
    param 21
    link t3
    t1 = call t2, 1
    print t1
```

### Clases: `new`, constructor, atributos y `this`

`new` reserva el objeto, guarda su tabla de métodos, corre los valores
iniciales de los atributos y llama al constructor con el objeto como primer
parámetro.

```
class Punto {
  let x: integer;
  let y: integer = 0;
  let nombre: string = "origen";

  function constructor(x: integer, y: integer) {
    this.x = x;
    this.y = y;
  }
  ...
}

let p: Punto = new Punto(3, 4);
```

```
    t1 = alloc 24                   ; let p: Punto = new Punto(3, 4);
    t1[0] = Punto.vtable
    param t1
    call Punto.init, 1
    param t1
    param 3
    param 4
    call Punto.constructor, 3
    G[0] = t1
```

```
func Punto.init, 48
    t1 = fp[32]                     ; let y: integer = 0;
    t1[12] = 0
    t1 = fp[32]                     ; let nombre: string = "origen";
    t1[16] = "origen"
endfunc

func Punto.constructor, 56
    t1 = fp[32]                     ; this.x = x;
    t1[8] = fp[40]
    t1 = fp[32]                     ; this.y = y;
    t1[12] = fp[44]
endfunc
```

`Clase.init` solo existe si la clase, o alguno de sus ancestros, tiene
atributos con valor inicial, y empieza llamando al `init` del padre. Un
atributo usado sin `this` dentro de un método se traduce igual que
`this.atributo`.

### Métodos y despacho dinámico

La dirección del método se busca en la tabla del objeto, no en la clase de la
variable. Así se ejecuta el método sobrescrito aunque la variable sea del tipo
padre:

```
let a: Animal = new Perro("Toby");
print(a.hablar());
```

```
    t1 = G[0]                       ; print(a.hablar());
    t1 = t1[0]
    t1 = t1[0]
    param G[0]
    t2 = call t1, 1
    print t2
```

`t1[0]` es la tabla de `Perro`, y su ranura `[0]` es `Perro.hablar`. Para
`b: Animal = new Animal(...)` las mismas instrucciones llegan a `Animal.hablar`.
`Perro` no declara constructor, así que `new Perro("Toby")` llama a
`Animal.constructor`, el que hereda.

Desde una función anidada dentro de un método, `this` se alcanza subiendo al
registro del método:

```
    t1 = access 1                   ; return this.x * k;
    t1 = t1[32]
    t1 = t1[8]
```

### Listas e índices

```
let numeros: integer[] = [4, 8, 15];
numeros[1] = numeros[0] + numeros[2];
matriz[1][0] = 9;
```

```
    t1 = alloc 20                   ; let numeros: integer[] = [4, 8, 15];
    t1[0] = 3
    t1[8] = 4
    t1[12] = 8
    t1[16] = 15
    G[0] = t1
    t1 = G[0]                       ; numeros[1] = numeros[0] + numeros[2];
    t2 = G[0]
    t2 = t2[8]
    t3 = G[0]
    t3 = t3[16]
    t2 = t2 + t3
    t1[12] = t2
    t1 = G[16]                      ; matriz[1][0] = 9;
    t1 = t1[16]
    t1[8] = 9
```

Con un índice constante, el desplazamiento se calcula al compilar. Con un
índice variable se calcula en ejecución:

```
    t2 = G[40] * 4
    t2 = t2 + 8
    t1 = t1[t2]
```

`lista.length` lee el encabezado: `t1 = t1[0]`.

### `foreach`

Se traduce a un bucle por índice. La lista y el índice quedan en temporales
durante todo el cuerpo.

```
foreach (n in numeros) {
  total = total + n;
}
```

```
    t1 = G[0]
    t2 = 0
L1:
    t3 = t1[0]
    if t2 >= t3 goto L3
    t3 = t2 * 4
    t3 = t3 + 8
    t3 = t1[t3]
    G[36] = t3
    t3 = G[32] + G[36]              ; total = total + n;
    G[32] = t3
L2:
    t2 = t2 + 1
    goto L1
L3:
```

### `try/catch`

```
try {
  print(lista[i]);
} catch (error) {
  print("fallo: " + error);
}
```

```
    try L1, G[12]
    t1 = G[0]                       ; print(lista[i]);
    t2 = G[8] * 4
    t2 = t2 + 8
    t1 = t1[t2]
    print t1
    endtry
    goto L2
L1:
    t1 = concat "fallo: ", G[12]    ; print("fallo: " + error);
    print t1
L2:
```

### Conversiones, concatenación y ternario

`integer` se promueve a `float` en una operación mixta, en una asignación, al
pasar un argumento y al retornar. Una constante entera se convierte al
compilar; cualquier otra cosa, con `(float)`.

```
let real: float = entero;
let suma: float = real + entero * 2;
```

```
    t1 = (float) G[0]               ; let real: float = entero;
    G[4] = t1
    t1 = G[0] * 2                   ; let suma: float = real + entero * 2;
    t1 = (float) t1
    t1 = G[4] + t1
    G[12] = t1
```

`"hola" + " " + "mundo"` se traduce a `t1 = concat "hola", " "` y luego a
`t1 = concat t1, "mundo"`. El ternario usa los mismos saltos que un
`if/else`, con un temporal para el resultado:

```
    ifFalse G[0] > 0 goto L1        ; let signo: string = entero > 0 ? "positivo" : "negativo";
    t1 = "positivo"
    goto L2
L1:
    t1 = "negativo"
L2:
    G[36] = t1
```

## Supuestos

- **No hay comprobaciones en tiempo de ejecución.** Un índice fuera de rango,
  la división entre cero o un acceso a `null` no generan código de
  verificación. Lo que valida el compilador lo valida el análisis semántico.
- **Una función no puede escapar del registro de activación que la contiene.**
  La gramática no tiene tipos función, así que no se puede declarar un
  parámetro ni un retorno de ese tipo. Por eso los registros de activación
  viven en una pila y alcanza con el enlace de acceso. El único escape posible
  sería asignar una función anidada a una variable global de la misma firma, y
  no está soportado.
- **Las funciones del nivel superior no reciben enlace de acceso.** Su
  registro tiene la ranura, pero queda en `null`: todo lo que ven fuera de sí
  mismas es global y tiene dirección fija.
- **Un constructor propio no llama al del padre.** La gramática no tiene
  `super`. Los atributos heredados sí reciben sus valores iniciales, porque
  `Clase.init` llama primero al `init` del padre.
- **Un método no se usa como valor y una sobrescritura conserva la firma.** Son
  las reglas semánticas `K5` y `K4`: sin ellas, un método tomado como valor
  perdería su `this`, y una llamada a través del tipo padre pasaría argumentos
  equivocados.
- **Una clase declarada dentro de una función no usa las locales de esa
  función.** Es la regla `K6`. Sus métodos se invocan por la tabla de métodos
  y no reciben enlace de acceso, así que ese registro de activación les es
  inalcanzable. Sí pueden usar sus propios atributos y los globales.
- **`==` sobre `string` compara contenido; sobre objetos y listas, direcciones.**
- **El `catch` recibe el mensaje como `string`,** que es el tipo que le da el
  análisis semántico.

## Cómo se verifica

Cada programa de `tests/tac/` declara qué construcciones traduce y tiene al
lado el TAC esperado:

```
// @esperado: TAC
// @genera: I11, I12, I13, I18
```

La batería genera el TAC y lo compara línea por línea con el `.tac`; si
difieren, señala la primera línea distinta. Un `.tac` se fija solo después de
revisarlo a mano contra el fuente. Además, la batería comprueba que ningún
programa con errores produzca código intermedio, e imprime la matriz de
cobertura de las 24 construcciones: si alguna queda sin caso, la batería falla.
