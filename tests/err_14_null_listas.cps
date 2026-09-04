// @esperado: ERRORES 5
// @regla: L1, T6
// Regresion: [null] se confundia con [].
//
// visitArrayLiteral devuelve ArrayType(NULL) para los dos, e
// isEmptyArrayLiteral comparaba la FORMA del tipo, no la identidad. Asi que
// [null] entraba por el atajo del literal vacio -- que cabe en cualquier lista --
// y `let a: integer[] = [null];` pasaba limpio, dejando un null adentro de una
// lista de enteros sobre el que despues se podia hacer aritmetica.
//
// Ahora el atajo es solo para el centinela [], y [null] se acepta unicamente
// donde el elemento admite null. El lado que SI debe funcionar esta en ok_11.

let a: integer[] = [null];
let b: integer[] = [null, null];
let c: boolean[] = [null];
let d: string[]  = [null];

// Y el mensaje ya no dice "lista vacia" sobre una lista que tiene un elemento.
let e = [null];

print(a.length); print(b.length); print(c.length); print(d.length);
