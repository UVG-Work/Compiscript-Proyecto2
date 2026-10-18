vtable Animal.vtable: Animal.hablar, Animal.presentar
vtable Perro.vtable: Perro.hablar, Animal.presentar

func main, 48
    t1 = alloc 32                   ; let a: Animal = new Perro("Toby");
    t1[0] = Perro.vtable
    param t1
    call Perro.init, 1
    param t1
    param "Toby"
    call Animal.constructor, 2
    G[0] = t1
    t1 = alloc 24                   ; let b: Animal = new Animal("Gato");
    t1[0] = Animal.vtable
    param t1
    call Animal.init, 1
    param t1
    param "Gato"
    call Animal.constructor, 2
    G[8] = t1
    t1 = G[0]                       ; print(a.hablar());
    t1 = t1[0]
    t1 = t1[0]
    param G[0]
    t2 = call t1, 1
    print t2
    t1 = G[0]                       ; print(a.presentar());
    t1 = t1[0]
    t1 = t1[8]
    param G[0]
    t2 = call t1, 1
    print t2
    t1 = G[8]                       ; print(b.hablar());
    t1 = t1[0]
    t1 = t1[0]
    param G[8]
    t2 = call t1, 1
    print t2
endfunc

func Animal.init, 48
    t1 = fp[32]                     ; let sonido: string = "...";
    t1[16] = "..."
endfunc

func Animal.constructor, 56
    t1 = fp[32]                     ; this.nombre = nombre;
    t1[8] = fp[40]
endfunc

func Animal.hablar, 56
    t1 = fp[32]                     ; return this.nombre + " dice " + this.sonido;
    t1 = t1[8]
    t1 = concat t1, " dice "
    t2 = fp[32]
    t2 = t2[16]
    t1 = concat t1, t2
    return t1
endfunc

func Animal.presentar, 64
    t1 = fp[32]                     ; return "Soy " + this.nombre + ". " + this.hablar();
    t1 = t1[8]
    t1 = concat "Soy ", t1
    t1 = concat t1, ". "
    t2 = fp[32]
    t2 = t2[0]
    t2 = t2[0]
    param fp[32]
    t3 = call t2, 1
    t1 = concat t1, t3
    return t1
endfunc

func Perro.init, 48
    param fp[32]
    call Animal.init, 1
    t1 = fp[32]                     ; let raza: string = "mestizo";
    t1[24] = "mestizo"
endfunc

func Perro.hablar, 48
    t1 = fp[32]                     ; return this.nombre + " ladra";
    t1 = t1[8]
    t1 = concat t1, " ladra"
    return t1
endfunc
