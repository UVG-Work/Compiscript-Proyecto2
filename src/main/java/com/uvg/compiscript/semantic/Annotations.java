package com.uvg.compiscript.semantic;

import com.uvg.compiscript.symbols.Symbol;
import com.uvg.compiscript.types.Type;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

/**
 * Lo que el analisis semantico deja escrito sobre el arbol: a que simbolo
 * resuelve cada nombre y que tipo tiene cada expresion. El generador de codigo
 * lo consulta en vez de volver a resolver.
 */
public final class Annotations {

    private final ParseTreeProperty<Symbol> symbols = new ParseTreeProperty<>();
    private final ParseTreeProperty<Type> types = new ParseTreeProperty<>();

    public Symbol symbol(ParseTree node) {
        return symbols.get(node);
    }

    public Type type(ParseTree node) {
        return types.get(node);
    }

    void bind(ParseTree node, Symbol symbol) {
        if (symbol != null) {
            symbols.put(node, symbol);
        }
    }

    void type(ParseTree node, Type type) {
        if (type != null) {
            types.put(node, type);
        }
    }
}
