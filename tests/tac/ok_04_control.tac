func main, 40
    G[0] = 0                        ; let total: integer = 0;
    G[4] = 0                        ; let i: integer = 0;
    ifFalse G[0] == 0 goto L1
    G[0] = 1                        ; total = 1;
    goto L2
L1:
    G[0] = 2                        ; total = 2;
L2:
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
    G[8] = 0                        ; let j: integer = 0;
L9:
    ifFalse G[8] < 10 goto L11
    ifFalse G[8] == 3 goto L12
    goto L10                        ; continue;
L12:
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
    ifFalse G[8] == 8 goto L17
    goto L11                        ; break;
L17:
L10:
    t1 = G[8] + 1                   ; j = j + 1
    G[8] = t1
    goto L9
L11:
L18:
    t1 = G[0] - 1                   ; total = total - 1;
    G[0] = t1
    ifFalse G[0] < 0 goto L20
    goto L19                        ; break;
L20:
    goto L18
L19:
    print G[0]                      ; print(total);
endfunc
