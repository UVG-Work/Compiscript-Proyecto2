package com.uvg.compiscript.codegen;

import com.uvg.compiscript.symbols.FunctionSymbol;
import java.util.ArrayList;
import java.util.List;

/** El TAC de una funcion. {@code symbol} es null para el programa principal. */
final class FunctionCode {

    final String label;
    final FunctionSymbol symbol;
    final List<Instruction> code = new ArrayList<>();
    final TempPool temps = new TempPool();
    int frameSize;

    FunctionCode(String label, FunctionSymbol symbol) {
        this.label = label;
        this.symbol = symbol;
    }
}
