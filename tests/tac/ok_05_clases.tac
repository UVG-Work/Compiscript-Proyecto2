vtable Punto.vtable: Punto.mover, Punto.norma, Punto.escalar

func main, 48
    t1 = alloc 24                   ; let p: Punto = new Punto(3, 4);
    t1[0] = Punto.vtable
    param t1
    call Punto.init, 1
    param t1
    param 3
    param 4
    call Punto.constructor, 3
    G[0] = t1
    t1 = G[0]                       ; p.mover(2);
    t1 = t1[0]
    t1 = t1[0]
    param G[0]
    param 2
    t2 = call t1, 2
    t1 = G[0]                       ; p.nombre = "p";
    t1[16] = "p"
    t1 = G[0]                       ; print(p.norma());
    t1 = t1[0]
    t1 = t1[8]
    param G[0]
    t2 = call t1, 1
    print t2
    t1 = G[0]                       ; print(p.escalar(3));
    t1 = t1[0]
    t1 = t1[16]
    param G[0]
    param 3
    t2 = call t1, 2
    print t2
    t1 = G[0]                       ; print(p.nombre);
    t1 = t1[16]
    print t1
endfunc

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

func Punto.mover, 60
    t1 = fp[32]                     ; this.x = this.x + dx;
    t1 = t1[8]
    t1 = t1 + fp[40]
    t2 = fp[32]
    t2[8] = t1
    t1 = fp[32]                     ; return this.x;
    t1 = t1[8]
    return t1
endfunc

func Punto.norma, 64
    t1 = fp[32]                     ; return x * x + y * y;
    t1 = t1[8]
    t2 = fp[32]
    t2 = t2[8]
    t1 = t1 * t2
    t2 = fp[32]
    t2 = t2[12]
    t3 = fp[32]
    t3 = t3[12]
    t2 = t2 * t3
    t1 = t1 + t2
    return t1
endfunc

func Punto.escalar, 52
    link fp                         ; return aplicar();
    t1 = call Punto.escalar.aplicar, 0
    return t1
endfunc

func Punto.escalar.aplicar, 48
    t1 = access 1                   ; return this.x * k;
    t1 = t1[32]
    t1 = t1[8]
    t2 = access 1
    t2 = t2[40]
    t1 = t1 * t2
    return t1
endfunc
