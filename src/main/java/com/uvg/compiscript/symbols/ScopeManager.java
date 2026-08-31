package com.uvg.compiscript.symbols;

/** Lleva el ambito actual durante el recorrido del arbol. */
public class ScopeManager {

    private final Scope global = new Scope("global", Scope.Kind.GLOBAL, null);
    private Scope current = global;

    public Scope push(String name, Scope.Kind kind) {
        current = new Scope(name, kind, current);
        return current;
    }

    /** Vuelve a entrar a un ambito ya creado (el de miembros de una clase). */
    public void enter(Scope scope) {
        current = scope;
    }

    public void pop() {
        if (current.getParent() != null) {
            current = current.getParent();
        }
    }

    public Scope current() {
        return current;
    }

    public Scope getRoot() {
        return global;
    }

    public boolean define(Symbol symbol) {
        return current.define(symbol);
    }

    public Symbol resolve(String name) {
        return current.resolve(name);
    }
}
