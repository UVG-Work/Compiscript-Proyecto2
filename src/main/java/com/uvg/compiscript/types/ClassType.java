package com.uvg.compiscript.types;

public final class ClassType extends Type {

    private final String name;
    private ClassType parent;

    public ClassType(String name) {
        this.name = name;
    }

    public ClassType getParent() {
        return parent;
    }

    /** La herencia se resuelve despues de registrar todas las clases. */
    public void setParent(ClassType parent) {
        this.parent = parent;
    }

    public boolean isSubclassOf(ClassType other) {
        for (ClassType c = this; c != null; c = c.parent) {
            if (c.name.equals(other.name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int size() {
        return 8;
    }

    @Override
    public boolean isAssignableFrom(Type source) {
        if (source instanceof ErrorType || source == PrimitiveType.NULL) {
            return true;
        }
        return source instanceof ClassType other && other.isSubclassOf(this);
    }
}
