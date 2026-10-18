func main, 48
    G[0] = 5                        ; let entero: integer = 5;
    t1 = (float) G[0]               ; let real: float = entero;
    G[4] = t1
    t1 = G[0] * 2                   ; let suma: float = real + entero * 2;
    t1 = (float) t1
    t1 = G[4] + t1
    G[12] = t1
    t1 = (float) G[0]               ; let otra: float = mitad(entero);
    param t1
    t1 = call mitad, 1
    G[20] = t1
    t1 = concat "hola", " "         ; let saludo: string = "hola" + " " + "mundo";
    t1 = concat t1, "mundo"
    G[28] = t1
    ifFalse G[0] > 0 goto L1        ; let signo: string = entero > 0 ? "positivo" : "negativo";
    t1 = "positivo"
    goto L2
L1:
    t1 = "negativo"
L2:
    G[36] = t1
    ifFalse G[0] < 3 goto L3        ; let limite: float = entero < 3 ? 1.5 : entero;
    t1 = 1.5
    goto L4
L3:
    t2 = (float) G[0]
    t1 = t2
L4:
    G[44] = t1
    print G[4]                      ; print(real);
    print G[12]                     ; print(suma);
    print G[20]                     ; print(otra);
    print G[28]                     ; print(saludo);
    print G[36]                     ; print(signo);
    print G[44]                     ; print(limite);
endfunc

func mitad, 48
    t1 = fp[32] / 2.0               ; return x / 2;
    return t1
endfunc
