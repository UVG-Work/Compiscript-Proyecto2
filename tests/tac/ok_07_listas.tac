func main, 56
    t1 = alloc 20                   ; let numeros: integer[] = [4, 8, 15];
    t1[0] = 3
    t1[8] = 4
    t1[12] = 8
    t1[16] = 15
    G[0] = t1
    t1 = alloc 8                    ; let vacia: integer[] = [];
    t1[0] = 0
    G[8] = t1
    t1 = alloc 24                   ; let matriz: integer[][] = [[1, 2], [3, 4]];
    t1[0] = 2
    t2 = alloc 16
    t2[0] = 2
    t2[8] = 1
    t2[12] = 2
    t1[8] = t2
    t2 = alloc 16
    t2[0] = 2
    t2[8] = 3
    t2[12] = 4
    t1[16] = t2
    G[16] = t1
    t1 = alloc 24                   ; let nombres: string[] = ["ana", "beto"];
    t1[0] = 2
    t1[8] = "ana"
    t1[16] = "beto"
    G[24] = t1
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
    G[32] = 0                       ; let total: integer = 0;
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
    G[40] = 0                       ; let i: integer = 0;
L4:
    t1 = G[0]
    t1 = t1[0]
    ifFalse G[40] < t1 goto L6
    t1 = G[0]                       ; total = total + numeros[i];
    t2 = G[40] * 4
    t2 = t2 + 8
    t1 = t1[t2]
    t1 = G[32] + t1
    G[32] = t1
L5:
    t1 = G[40] + 1                  ; i = i + 1
    G[40] = t1
    goto L4
L6:
    print G[32]                     ; print(total);
    t1 = G[16]                      ; print(matriz[1][0]);
    t1 = t1[16]
    t1 = t1[8]
    print t1
    t1 = G[8]                       ; print(vacia.length);
    t1 = t1[0]
    print t1
    t1 = G[24]                      ; print(nombres[1]);
    t1 = t1[16]
    print t1
endfunc
