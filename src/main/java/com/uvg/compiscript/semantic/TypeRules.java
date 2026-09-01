package com.uvg.compiscript.semantic;

import com.uvg.compiscript.types.ArrayType;
import com.uvg.compiscript.types.ClassType;
import com.uvg.compiscript.types.ErrorType;
import com.uvg.compiscript.types.PrimitiveType;
import com.uvg.compiscript.types.Type;

/**
 * Las reglas de compatibilidad, en un solo lugar.
 *
 * <p>{@link ErrorType} es asignable a todo y absorbe cualquier operacion: es lo
 * que evita que un error real produzca una cascada de mensajes derivados.
 */
public final class TypeRules {

    /** El literal {@code []}: un arreglo cuyo elemento todavia no se conoce. */
    public static final ArrayType EMPTY_ARRAY = new ArrayType(PrimitiveType.NULL);

    private TypeRules() {
    }

    /**
     * Una lista de errores tambien es un error. Sin mirar dentro del
     * {@link ArrayType}, {@code [1, [2]]} o {@code Desconocido[]} valen
     * {@code <error>[]}, que no es {@code ErrorType}, y el sumidero deja pasar
     * un segundo mensaje derivado del primero.
     */
    public static boolean isError(Type type) {
        if (type == null || type instanceof ErrorType) {
            return true;
        }
        return type instanceof ArrayType array && isError(array.getElementType());
    }

    /** Tipos que admiten {@code null}. */
    public static boolean isReference(Type type) {
        return type instanceof ClassType || type instanceof ArrayType
                || type == PrimitiveType.NULL;
    }

    public static boolean isNumeric(Type type) {
        return type instanceof PrimitiveType primitive && primitive.isNumeric();
    }

    public static boolean isEmptyArrayLiteral(Type type) {
        return type instanceof ArrayType array && array.getElementType() == PrimitiveType.NULL;
    }

    /** true si un valor de {@code source} cabe en un destino de tipo {@code target}. */
    public static boolean assignable(Type target, Type source) {
        if (isError(target) || isError(source)) {
            return true;
        }
        if (source == PrimitiveType.NULL) {
            return isReference(target);
        }
        if (target instanceof ArrayType targetArray && source instanceof ArrayType sourceArray) {
            if (isEmptyArrayLiteral(sourceArray)) {
                return true;
            }
            if (targetArray.getElementType() instanceof ArrayType
                    && sourceArray.getElementType() instanceof ArrayType) {
                return assignable(targetArray.getElementType(), sourceArray.getElementType());
            }
            // Invariantes: Perro[] no cabe en Animal[], porque escribir un Gato
            // en el destino corromperia el origen.
            return targetArray.getElementType().equalsType(sourceArray.getElementType());
        }
        return target.isAssignableFrom(source);
    }

    /** Para {@code ==} y {@code !=}: basta que uno de los dos quepa en el otro. */
    public static boolean comparable(Type a, Type b) {
        if (isError(a) || isError(b)) {
            return true;
        }
        if (a == PrimitiveType.NULL || b == PrimitiveType.NULL) {
            return isReference(a) && isReference(b);
        }
        return assignable(a, b) || assignable(b, a);
    }

    /**
     * Tipo comun de dos ramas. Devuelve null si no lo hay. Lo usan el ternario y
     * los literales de lista.
     */
    public static Type unify(Type a, Type b) {
        if (isError(a)) {
            return b;
        }
        if (isError(b)) {
            return a;
        }
        if (a.equalsType(b)) {
            return a;
        }
        // integer y float: promueve al mas ancho, igual que en las operaciones.
        if (isNumeric(a) && isNumeric(b)) {
            return PrimitiveType.FLOAT;
        }
        if (a == PrimitiveType.NULL) {
            return isReference(b) ? b : null;
        }
        if (b == PrimitiveType.NULL) {
            return isReference(a) ? a : null;
        }
        if (isEmptyArrayLiteral(a) && b instanceof ArrayType) {
            return b;
        }
        if (isEmptyArrayLiteral(b) && a instanceof ArrayType) {
            return a;
        }
        if (a instanceof ArrayType arrayA && b instanceof ArrayType arrayB) {
            Type element = unify(arrayA.getElementType(), arrayB.getElementType());
            return element == null ? null : new ArrayType(element);
        }
        if (a instanceof ClassType classA && b instanceof ClassType classB) {
            for (ClassType c = classA; c != null; c = c.getParent()) {
                if (classB.isSubclassOf(c)) {
                    return c;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Camino de herencia con liebre y tortuga. Un ciclo {@code A : B, B : A} colgaria
     * cualquier recorrido de la cadena, asi que se detecta antes de usarla.
     */
    public static boolean hasInheritanceCycle(ClassType start) {
        ClassType slow = start;
        ClassType fast = start;
        while (fast != null && fast.getParent() != null) {
            slow = slow.getParent();
            fast = fast.getParent().getParent();
            if (slow == fast) {
                return true;
            }
        }
        return false;
    }
}
