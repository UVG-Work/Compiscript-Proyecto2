package com.uvg.compiscript.symbols;

import com.uvg.compiscript.types.ClassType;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

public class ClassSymbol extends Symbol {

    private ClassSymbol parent;
    private Scope memberScope;
    private int instanceSize = -1;
    private final List<VariableSymbol> fields = new ArrayList<>();
    private final List<FunctionSymbol> vtable = new ArrayList<>();

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

    /** Bytes de una instancia, con la tabla de metodos y los atributos heredados; -1 sin calcular. */
    public int getInstanceSize() {
        return instanceSize;
    }

    public void setInstanceSize(int instanceSize) {
        this.instanceSize = instanceSize;
    }

    /** Todos los atributos de una instancia en orden de memoria, heredados primero. */
    public List<VariableSymbol> getFields() {
        return fields;
    }

    /** Un metodo por ranura; la subclase reemplaza la ranura del metodo que sobrescribe. */
    public List<FunctionSymbol> getVtable() {
        return vtable;
    }

    public int slotOf(String method) {
        for (int i = 0; i < vtable.size(); i++) {
            if (vtable.get(i).getName().equals(method)) {
                return i;
            }
        }
        return -1;
    }

    public String getVtableLabel() {
        return getName() + ".vtable";
    }

    @Override
    public String getKind() {
        return "class";
    }
}
