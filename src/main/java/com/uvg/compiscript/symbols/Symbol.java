package com.uvg.compiscript.symbols;

import com.uvg.compiscript.parser.CompiscriptLexer;
import com.uvg.compiscript.types.Type;
import org.antlr.v4.runtime.Token;

/**
 * Una entrada de la tabla de simbolos.
 *
 * <p>Los atributos son los que el curso pide como minimo: lexema, token, tipo de
 * dato, alcance, posicion de memoria, linea y columna, y tipo semantico. Los de
 * funciones (numero y tipo de parametros, y metodo de paso) viven en
 * {@link FunctionSymbol} y {@link ParameterSymbol}.
 */
public abstract class Symbol {

    private final String name;
    private final Token declaration;
    private Type type;
    private int offset = -1;
    private Scope scope;

    protected Symbol(String name, Type type, Token declaration) {
        this.name = name;
        this.type = type;
        this.declaration = declaration;
    }

    /** Lexema. */
    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    /** El token del lexema, tal como lo clasifico el lexer. */
    public Token getDeclaration() {
        return declaration;
    }

    public int getTokenType() {
        return declaration.getType();
    }

    /** Nombre del token: {@code Identifier} para todo lo que se declara. */
    public String getTokenName() {
        String symbolic = CompiscriptLexer.VOCABULARY.getSymbolicName(getTokenType());
        if (symbolic != null) {
            return symbolic;
        }
        String literal = CompiscriptLexer.VOCABULARY.getLiteralName(getTokenType());
        return literal != null ? literal : "?";
    }

    public int getLine() {
        return declaration.getLine();
    }

    public int getColumn() {
        return declaration.getCharPositionInLine();
    }

    /** Alcance: el ambito donde se declaro. Lo asigna {@link Scope#define}. */
    public Scope getScope() {
        return scope;
    }

    void setScope(Scope scope) {
        this.scope = scope;
    }

    public int getScopeDepth() {
        return scope == null ? -1 : scope.getDepth();
    }

    /** Posicion de memoria: desplazamiento dentro de su ambito. */
    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    /** Tipo semantico. Etiqueta corta para la tabla de simbolos del IDE. */
    public abstract String getKind();

    @Override
    public String toString() {
        return String.format("%s %s : %s", getKind(), name, type);
    }
}
