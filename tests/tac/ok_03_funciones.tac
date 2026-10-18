func main, 40
    param 5                         ; print(factorial(5));
    t1 = call factorial, 1
    print t1
    param 3                         ; print(sumar(factorial(3), 4));
    t1 = call factorial, 1
    param t1
    param 4
    t1 = call sumar, 2
    print t1
    param 10                        ; print(crearContador(10));
    t1 = call crearContador, 1
    print t1
    param "listo"                   ; anunciar("listo");
    call anunciar, 1
endfunc

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

func sumar, 48
    t1 = fp[32] + fp[36]            ; return a + b;
    return t1
endfunc

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

func anunciar, 40
    print fp[32]                    ; print(mensaje);
endfunc
