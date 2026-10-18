func main, 48
    t1 = alloc 20                   ; let lista: integer[] = [1, 2, 3];
    t1[0] = 3
    t1[8] = 1
    t1[12] = 2
    t1[16] = 3
    G[0] = t1
    G[8] = 5                        ; let i: integer = 5;
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
    print "fin"                     ; print("fin");
endfunc
