func main, 56
    t1 = alloc 16                   ; let f = doble;
    t1[0] = doble
    t1[8] = null
    G[0] = t1
    t1 = G[0]                       ; print(f(21));
    t2 = t1[0]
    t3 = t1[8]
    param 21
    link t3
    t1 = call t2, 1
    print t1
    param 10                        ; print(externa(10));
    t1 = call externa, 1
    print t1
endfunc

func externa, 72
    fp[36] = fp[32]                 ; let total: integer = base;
    t1 = alloc 16                   ; let g = media;
    t1[0] = externa.media
    t1[8] = fp
    fp[40] = t1
    t1 = fp[40]                     ; return g(1);
    t2 = t1[0]
    t3 = t1[8]
    param 1
    link t3
    t1 = call t2, 1
    return t1
endfunc

func externa.media, 52
    param fp[32]                    ; return interna(n) + interna(n);
    link fp
    t1 = call externa.media.interna, 1
    param fp[32]
    link fp
    t2 = call externa.media.interna, 1
    t1 = t1 + t2
    return t1
endfunc

func externa.media.interna, 52
    t1 = access 2                   ; total = total + k;
    t1 = t1[36]
    t1 = t1 + fp[32]
    t2 = access 2
    t2[36] = t1
    t1 = access 2                   ; return total;
    t1 = t1[36]
    return t1
endfunc

func doble, 44
    t1 = fp[32] * 2                 ; return x * 2;
    return t1
endfunc
