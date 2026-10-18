vtable Caja.vtable: Caja.init$2

func main, 48
    G[0] = 3                        ; let n: integer = 3;
    t1 = alloc 32                   ; let reales: float[] = [1, 2.5, n];
    t1[0] = 3
    t1[8] = 1.0
    t1[16] = 2.5
    t2 = (float) G[0]
    t1[24] = t2
    G[4] = t1
    t1 = alloc 24                   ; let matriz: float[][] = [[1, 2], [3.5]];
    t1[0] = 2
    t2 = alloc 24
    t2[0] = 2
    t2[8] = 1.0
    t2[16] = 2.0
    t1[8] = t2
    t2 = alloc 16
    t2[0] = 1
    t2[8] = 3.5
    t1[16] = t2
    G[12] = t1
    t1 = alloc 12                   ; let caja: Caja = new Caja();
    t1[0] = Caja.vtable
    param t1
    call Caja.init, 1
    G[20] = t1
    t1 = G[12]                      ; print(matriz[0][1]);
    t1 = t1[8]
    t1 = t1[16]
    print t1
    t1 = G[4]                       ; print(reales[2]);
    t1 = t1[24]
    print t1
    t1 = G[20]                      ; print(caja.init());
    t1 = t1[0]
    t1 = t1[0]
    param G[20]
    t2 = call t1, 1
    print t2
    t1 = call main$2, 0             ; print(main());
    print t1
    param true                      ; print(elegir(true));
    t1 = call elegir, 1
    print t1
endfunc

func lejos, 64
    t1 = fp[32]                     ; return a[99999999999] + a[600000000];
    t2 = 99999999999 * 4
    t2 = t2 + 8
    t1 = t1[t2]
    t2 = fp[32]
    t3 = 600000000 * 4
    t3 = t3 + 8
    t2 = t2[t3]
    t1 = t1 + t2
    return t1
endfunc

func main$2, 32
    return 1                        ; return 1;
endfunc

func elegir, 41
    ifFalse fp[32] goto L1
    link fp                         ; return h();
    t1 = call elegir.h, 0
    return t1
    goto L2
L1:
    link fp                         ; return h();
    t1 = call elegir.h$2, 0
    return t1
L2:
endfunc

func elegir.h, 32
    return 10                       ; return 10;
endfunc

func elegir.h$2, 32
    return 20                       ; return 20;
endfunc

func Caja.init, 48
    t1 = fp[32]                     ; let valor: integer = 5;
    t1[8] = 5
endfunc

func Caja.init$2, 48
    t1 = fp[32]                     ; return this.valor;
    t1 = t1[8]
    return t1
endfunc
