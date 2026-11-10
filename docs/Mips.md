# Generación de código MIPS

La última fase traduce el código de tres direcciones a ensamblador MIPS de 32
bits y lo ejecuta en el simulador [MARS 4.5](https://github.com/dpetersanderson/MARS)
(`lib/Mars4_5.jar`, licencia MIT). Cubre las secuencias de llamada y retorno,
la asignación de registros con `getReg()` y el código de cada instrucción del
TAC.

```
fuente .cps → lexer/parser → semántico → memoria → TAC → MIPS → MARS
```

```bash
java -jar target/compiscript.jar tests/mips/ok_05_funciones.cps --mips   # el .asm
java -jar target/compiscript.jar tests/mips/ok_05_funciones.cps --run    # lo ejecuta
```

Todos los fragmentos de este documento están copiados de la salida real.

## El TAC es la frontera

El generador de MIPS (`com.uvg.compiscript.mips`) no mira el árbol: lee el
mismo texto que imprime `--tac`, instrucción por instrucción. Lo único que el
TAC no dice y la máquina sí necesita es el **tipo** con que se opera: `t1 = a + b`
es `addu` si son enteros y `add.s` si son `float`; `print x` llama a una rutina
distinta para cada tipo; `x == y` compara contenido si son `string`. Por eso cada
instrucción lleva, además de su texto, un tipo que no se imprime y que el
generador de TAC llena solo donde importa: operaciones, comparaciones, `print`
y accesos a listas.

| Paquete / clase | Qué hace |
|---|---|
| `Tac` | Desarma una línea de TAC en su forma (`x = y op z`, `x = t[i]`, `if y op z goto L`…). |
| `Liveness` | Bloques básicos y vida de los temporales. |
| `RegisterAllocator` | `getReg()`: descriptores de registros y direcciones, desalojo y derrame. |
| `FunctionTranslator` | Prólogo, cuerpo y epílogo de una función. |
| `MipsGenerator` | Área estática, tablas de métodos, cadenas y arranque del programa. |
| `MipsRuntime` | Rutinas de apoyo: cadenas, `print`, errores y `try/catch`. |
| `MarsRunner` | Ejecuta el `.asm` en MARS como proceso aparte. |

## Modelo de memoria en MIPS

Se conserva **exactamente** el modelo del TAC: los desplazamientos `G[n]`,
`fp[n]`, `+n` de un objeto y `8 + i * tamaño` de una lista son los mismos bytes
en MIPS. Lo que se fija aquí es cómo vive cada valor en una máquina de 32 bits:

- Todo valor es **una palabra de 32 bits** en un registro de propósito general:
  enteros, booleanos (0 o 1), direcciones y `float`. Un `float` es IEEE de
  precisión simple; sus bits se pasan a la FPU (`mtc1`) solo para operar.
- Las referencias y los `float` conservan su ranura de 8 bytes del TAC y usan
  la primera palabra. Las ranuras de la tabla de métodos también miden 8: cada
  entrada es `.word metodo, 0`.
- `boolean` ocupa 4 bytes, no 1. Con 1 byte, la variable siguiente quedaba en
  una dirección impar y `lw` exige direcciones múltiplo de 4. Ahora todo
  tamaño es 4 u 8 y todo acceso está alineado.
- `G` es un `.space` en `.data`; `G[n]` es `G+n`. El heap crece con `sbrk`
  (syscall 9); `alloc n` pide `n` redondeado a palabra.

## Secuencia de llamada y retorno

El registro de activación es el del TAC, con `$fp` apuntando a su inicio y el
marco ocupando `[$fp, $fp + tamaño)`. Al final se agregan las palabras donde la
función guarda los `$s` que usa.

| Desplazamiento | Contenido |
|---|---|
| `0($fp)` | Enlace de control: el `$fp` de quien llamó. |
| `8($fp)` | Enlace de acceso, que llega en `$v1`. |
| `16($fp)` | Dirección de retorno (`$ra`). |
| `24($fp)` | Valor de retorno (también queda en `$v0`). |
| `32($fp)` … | `this`, parámetros, locales y temporales, como en el TAC. |
| `tamaño($fp)` … | Los `$s` que la función preserva. |

**Quien llama:**

1. Los cuatro primeros argumentos van en `$a0-$a3`; los demás se apilan, en
   orden, bajando `$sp` una palabra por cada uno.
2. El enlace de acceso va en `$v1` (`link`), o `$zero` para una función del
   nivel superior.
3. Antes del salto escribe a memoria los registros sucios que siguen vivos
   (los `$t` son *caller-saved*), y después olvida todos los registros: la
   función llamada usa los mismos `$t` y puede modificar globales y, por el
   enlace de acceso, las locales del llamador.
4. `jal etiqueta`, o `jalr $reg` si la dirección está en un temporal (despacho
   por la tabla de métodos o función como valor).
5. Al volver desapila los argumentos que apiló y toma el resultado de `$v0`.

**La función llamada:**

1. Prólogo: baja `$sp` el tamaño del marco, guarda el enlace de control, el de
   acceso y `$ra`, fija `$fp`, copia los argumentos a sus desplazamientos y
   guarda los `$s` que va a usar (*callee-saved*).
2. `return x`: deja `x` en `$v0` y en `24($fp)`, escribe a memoria los globales
   sucios y salta al epílogo.
3. Epílogo: restaura los `$s` y `$ra`, sube `$sp` al tope del marco, restaura
   el `$fp` de quien llamó y `jr $ra`.

```
_factorial:
    addiu $sp, $sp, -48             # reserva el registro de activacion
    sw $fp, 0($sp)                  # enlace de control
    sw $v1, 8($sp)                  # enlace de acceso
    sw $ra, 16($sp)                 # direccion de retorno
    move $fp, $sp
    sw $a0, 32($fp)                 # argumento 1
    sw $s0, 44($fp)                 # preserva $s0
    ...
L1:
    # --- return n * factorial(n - 1);
    # t1 = fp[32] - 1
    lw $s0, 32($fp)                 # fp[32]
    addiu $t0, $s0, -1
    # param t1
    move $a0, $t0
    # t1 = call factorial, 1
    move $v1, $zero                 # sin enlace de acceso
    jal _factorial
    move $t0, $v0                   # valor de retorno
    # t1 = fp[32] * t1
    lw $s0, 32($fp)                 # fp[32]
    mul $t0, $s0, $t0
    # return t1
    move $v0, $t0
    sw $v0, 24($fp)                 # valor de retorno
    j ret_factorial
ret_factorial:
    lw $s0, 44($fp)
    lw $ra, 16($fp)
    addiu $sp, $fp, 48              # libera el registro de activacion
    lw $fp, 0($fp)                  # restaura el marco de quien llamo
    jr $ra
```

Con más de cuatro argumentos (`tests/mips/ok_05_funciones.cps`), los que
sobran pasan por la pila y quien llama los retira:

```
    li $t8, 6                       # 6
    addiu $sp, $sp, -4              # argumento 6 a la pila
    sw $t8, 0($sp)
    # t1 = call suma6, 6
    move $v1, $zero                 # sin enlace de acceso
    jal _suma6
    addiu $sp, $sp, 8               # desapila los argumentos
```

Cada línea del TAC aparece como comentario (`# t1 = call factorial, 1`) sobre
su traducción, y la sentencia del fuente como `# --- ...`.

## Asignación de registros: `getReg()`

Los registros son una **caché de la memoria dentro de un bloque básico**. Toda
variable y todo temporal tiene un lugar fijo en memoria (`G+n` o `n($fp)`; un
temporal `tk`, su ranura en el registro de activación), y `getReg()` decide qué
nombre está en qué registro.

| Registros | Uso |
|---|---|
| `$t0-$t7` | Temporales del TAC. |
| `$s0-$s7` | Variables (`G[n]`, `fp[n]`). La función los preserva. |
| `$t8`, `$t9` | Constantes y direcciones de paso; `getReg` nunca los asigna. |
| `$a0-$a3`, `$v0`, `$v1` | Argumentos, resultado y enlace de acceso. |
| `$f0`, `$f2`, `$f12` | Operaciones y `print` de `float`. |

Lleva dos descriptores: el de **registros** (qué nombre guarda cada uno) y el
de **direcciones** (en qué registro está cada nombre), y marca como *sucio* un
registro cuyo valor todavía no se escribió a memoria.

`getReg(nombre)`:

1. Si el nombre ya está en un registro, ese.
2. Si no, un registro libre de su clase (`$t` o `$s`), cargando el valor de
   memoria si la instrucción lo lee.
3. Si no hay libre, **desaloja** el registro cuyo nombre se va a leer más tarde
   dentro del bloque (o nunca), sin tocar los operandos de la instrucción
   actual. Si estaba sucio y su valor sigue vivo, primero lo **derrama** a su
   lugar en memoria: a la pila si es un temporal o una local, al área estática
   si es un global.

Para saber qué temporales siguen vivos, `Liveness` hace el análisis clásico
hacia atrás sobre los bloques básicos (`in = use ∪ (out − def)`, iterado hasta
que no cambia). Con eso:

- un temporal que una instrucción lee por última vez **suelta su registro**
  ahí mismo, y el resultado puede reutilizarlo;
- al final de un bloque y antes de una llamada solo se escriben los temporales
  sucios que siguen vivos; las variables sucias se escriben siempre;
- un temporal muerto se desaloja gratis, sin store.

Al empezar un bloque (una etiqueta, o la instrucción después de un salto) los
descriptores se vacían: por ese punto pueden pasar varios caminos y ningún
registro es confiable.

En `tests/mips/ok_11_registros.cps` una expresión deja once temporales vivos y
solo hay ocho `$t`; `getReg` derrama a la pila los que se usan más tarde, que
son los de la izquierda:

```
    # t9 = G[0] * 9
    li $t9, 9                       # 9
    sw $t0, 32($fp)                 # derrame de t1
    mul $t0, $s0, $t9
    # t10 = G[0] * 10
    li $t9, 10                      # 10
    sw $t1, 40($fp)                 # derrame de t2
```

La misma prueba usa doce variables en un bloque, y los `$s` se derraman igual.

## Runtime

Cada programa lleva unas rutinas en ensamblador (`MipsRuntime`). Solo usan
`$a0-$a3`, `$v0`, `$t8` y `$t9`, así que llamarlas no obliga a guardar los
registros que `getReg` tiene ocupados.

| Rutina | Qué hace |
|---|---|
| `rt_concat` | Concatena dos cadenas en una nueva en el heap. |
| `rt_streq` | `==` y `!=` sobre `string`: compara contenido. |
| `rt_print_str`, `rt_print_bool`, `rt_print_ref` | `print` de `string`, `boolean` y referencias (`null` o la dirección). `integer` y `float` usan las syscalls 1 y 2. |
| `rt_throw` | Lanza un error en tiempo de ejecución. |

### Errores en tiempo de ejecución y `try/catch`

El código revisa tres cosas: un divisor entero cero, un índice fuera del rango
de una lista (contra la longitud de su encabezado) y un acceso a través de
`null`. Cualquiera salta a `rt_throw` con su mensaje.

`try L, x` apila un manejador en una pila propia del runtime: la dirección de
`L` con el `$sp` y el `$fp` de ese momento. `endtry` lo desapila. `rt_throw`
desapila el más reciente, restaura `$sp` y `$fp` —eso **desenrolla la pila**
aunque el error haya ocurrido varias llamadas más abajo— y salta a `L` con el
mensaje en `$v0`, que `L` guarda en la variable del `catch`. Sin manejador, el
programa imprime `Error en tiempo de ejecucion: <mensaje>` y termina con código
1.

En una función con `try`, antes de cada instrucción que puede fallar se
escriben a memoria los registros sucios: el `catch` lee la memoria, no los
registros. Por eso en `seguro(1)` de `tests/mips/ok_10_excepciones.cps` el
`catch` ve `acumulado` ya incrementado y el resultado es `12`.

Salir de un `try` con `break`, `continue` o `return` emite los `endtry` que
correspondan antes del salto, así el manejador no queda activo fuera de su
bloque.

## Supuestos y límites

- **`float` es de precisión simple.** El TAC le reserva 8 bytes; MIPS usa 4.
- **Enteros de 32 bits.** Una constante que no cabe se trunca a 32 bits, igual
  que el desborde de una suma.
- **Los registros no sobreviven a un bloque básico ni a una llamada.** Es
  asignación local: correcta para cualquier flujo de control, a costa de
  recargar valores al entrar a cada bloque.
- **A lo sumo 1024 `try` activos a la vez** (por ejemplo, una recursión dentro
  de un `try`). La pila de manejadores no verifica su desborde.
- **Una función no escapa de su registro de activación**, como en el TAC.

## Cómo se verifica

Los casos de [`tests/mips/`](../tests/mips) se compilan, se ejecutan en MARS y
su salida se compara línea por línea con el `.out` del mismo nombre:

```
// @esperado: MIPS
// @mips: M07, M08, M09
```

`MIPS-ERROR` exige además que el programa termine con un error en tiempo de
ejecución (código 1). Un `.out` se fija solo después de revisar la salida a
mano contra el fuente. La batería también comprueba que ningún programa con
errores de compilación produzca ensamblador, e imprime la matriz de cobertura
de las 20 construcciones; si alguna queda sin caso, la batería falla.
