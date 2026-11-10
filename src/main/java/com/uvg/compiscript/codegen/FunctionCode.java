package com.uvg.compiscript.codegen;

import com.uvg.compiscript.symbols.FunctionSymbol;
import com.uvg.compiscript.symbols.MemoryLayout;
import java.util.ArrayList;
import java.util.List;

/** El TAC de una funcion. {@code symbol} es null para el programa principal. */
public final class FunctionCode {

    final String label;
    final FunctionSymbol symbol;
    final List<Instruction> code = new ArrayList<>();
    final TempPool temps = new TempPool();
    int frameSize;
    /** Bloques {@code try} abiertos en este punto de la generacion. */
    int openTries;

    FunctionCode(String label, FunctionSymbol symbol) {
        this.label = label;
        this.symbol = symbol;
    }

    public String label() {
        return label;
    }

    public FunctionSymbol symbol() {
        return symbol;
    }

    public List<Instruction> code() {
        return List.copyOf(code);
    }

    public int frameSize() {
        return frameSize;
    }

    /** Donde empiezan los temporales: despues de parametros y locales. */
    public int tempsStart() {
        return symbol == null ? MemoryLayout.FRAME_HEADER : symbol.getLocalsEnd();
    }
}
