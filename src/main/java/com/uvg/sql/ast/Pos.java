package com.uvg.sql.ast;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/** Ubicacion de un nodo en el texto: linea desde 1, columna desde 0. */
public record Pos(int line, int column) {

    public static Pos of(Token token) {
        return new Pos(token.getLine(), token.getCharPositionInLine());
    }

    public static Pos of(ParserRuleContext ctx) {
        return of(ctx.getStart());
    }
}
