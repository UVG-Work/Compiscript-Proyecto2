package com.uvg.compiscript.mips;

import com.uvg.compiscript.codegen.FunctionCode;
import com.uvg.compiscript.codegen.TacProgram;
import com.uvg.compiscript.symbols.ClassSymbol;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce el programa en TAC a un archivo de ensamblador MIPS para MARS.
 *
 * <p>Las etiquetas del TAC llevan delante un {@code _} ({@code _main},
 * {@code _Punto.mover}) y su {@code $} pasa a {@code .}: asi nunca chocan con
 * las etiquetas de salto {@code L1}, con las del runtime ({@code rt_...}) ni
 * con los nombres de instrucciones.
 */
public final class MipsGenerator {

    private final TacProgram program;
    private final Map<String, String> strings = new LinkedHashMap<>();

    public MipsGenerator(TacProgram program) {
        this.program = program;
    }

    public String generate() {
        Asm text = new Asm();
        text.raw("    .text");
        text.label("rt_start");
        text.op("move $v1, $zero");
        text.op("jal _main", "el programa principal");
        text.op("li $v0, 10", "exit");
        text.op("syscall");
        for (FunctionCode function : program.functions()) {
            new FunctionTranslator(this, function).translate(text);
        }
        text.raw(MipsRuntime.CODE);
        return data() + "\n" + text.text();
    }

    private String data() {
        Asm data = new Asm();
        data.raw("    .data");
        data.raw("    .align 2");
        data.label("G");
        data.op(".space " + Math.max(4, program.globalSize()), "area estatica");
        // Cada ranura mide 8 bytes, como toda referencia en el modelo de memoria.
        for (ClassSymbol type : program.classes()) {
            String methods = type.getVtable().stream()
                    .map(method -> label(method.getLabel()) + ", 0")
                    .collect(Collectors.joining(", "));
            data.label(label(type.getVtableLabel()));
            data.op(".word " + (methods.isEmpty() ? "0" : methods));
        }
        data.raw(MipsRuntime.DATA);
        strings.forEach((literal, name) -> {
            data.label(name);
            data.op(stringDirective(literal.substring(1, literal.length() - 1)));
        });
        return data.text();
    }

    /** La etiqueta de datos de una cadena literal; las repetidas se comparten. */
    String string(String literal) {
        return strings.computeIfAbsent(literal, key -> "str" + (strings.size() + 1));
    }

    /**
     * {@code .asciiz} si la cadena es ASCII imprimible sin barra invertida; si
     * no, sus bytes en UTF-8, porque MARS interpreta las barras como escapes.
     */
    private static String stringDirective(String value) {
        if (value.chars().allMatch(c -> c >= 0x20 && c < 0x7f && c != '\\')) {
            return ".asciiz \"" + value + "\"";
        }
        StringBuilder bytes = new StringBuilder(".byte ");
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            bytes.append(b).append(", ");
        }
        return bytes.append(0).toString();
    }

    static String label(String tacLabel) {
        return "_" + tacLabel.replace('$', '.');
    }
}
