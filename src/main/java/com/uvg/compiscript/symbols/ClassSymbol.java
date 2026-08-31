package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.ClassType;
import org.antlr.v4.runtime.Token;

public class ClassSymbol extends Symbol {

    private ClassSymbol parent;
    private Scope memberScope;

    public ClassSymbol(String name, ClassType type, Token declaration) {
        super(name, type, declaration);
    }

    public ClassType getClassType() {
        return (ClassType) getType();
    }

    public ClassSymbol getParent() {
        return parent;
    }

    public void setParent(ClassSymbol parent) {
        this.parent = parent;
    }

    public Scope getMemberScope() {
        return memberScope;
    }

    public void setMemberScope(Scope memberScope) {
        this.memberScope = memberScope;
    }

    /** Busca un miembro en esta clase y luego en sus ancestros. */
    public Symbol resolveMember(String name) {
        for (ClassSymbol c = this; c != null; c = c.parent) {
            if (c.memberScope != null) {
                Symbol found = c.memberScope.resolveLocal(name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public String getKind() {
        return "class";
    }
}
