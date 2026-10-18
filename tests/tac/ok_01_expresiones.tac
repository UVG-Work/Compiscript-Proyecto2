func main, 48
    G[0] = 10                       ; const BASE: integer = 10;
    G[4] = 7                        ; let a: integer = 7;
    G[8] = 3                        ; let b: integer = 3;
    t1 = G[8] * 2                   ; let c: integer = a + b * 2 - BASE / (a - b) % 4;
    t1 = G[4] + t1
    t2 = G[4] - G[8]
    t2 = G[0] / t2
    t2 = t2 % 4
    t1 = t1 - t2
    G[12] = t1
    t1 = G[4] - G[8]                ; let d: integer = a - b - 1;
    t1 = t1 - 1
    G[16] = t1
    t1 = - G[4]                     ; let e: integer = -a;
    G[20] = t1
    t1 = G[4] > G[8]                ; let mayor: boolean = a > b;
    G[24] = t1
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
    t1 = G[4] == G[8]               ; let igual: boolean = a == b;
    G[26] = t1
    t1 = G[4] + G[8]                ; c = c * (a + b);
    t1 = G[12] * t1
    G[12] = t1
    print G[12]                     ; print(c);
    print G[16]                     ; print(d);
    print G[20]                     ; print(e);
    print G[24]                     ; print(mayor);
    print G[25]                     ; print(rango);
    print G[26]                     ; print(igual);
endfunc
