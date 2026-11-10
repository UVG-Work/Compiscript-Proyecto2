package com.uvg.compiscript.mips;

/** Acumula lineas de ensamblador con un formato parejo. */
final class Asm {

    private static final int COMMENT_COLUMN = 36;

    private final StringBuilder out = new StringBuilder();

    void op(String instruction) {
        out.append("    ").append(instruction).append('\n');
    }

    void op(String instruction, String comment) {
        String line = "    " + instruction;
        out.append(line.length() < COMMENT_COLUMN
                ? String.format("%-" + COMMENT_COLUMN + "s", line) : line + "  ");
        out.append("# ").append(comment).append('\n');
    }

    void label(String label) {
        out.append(label).append(":\n");
    }

    void comment(String text) {
        out.append("    # ").append(text).append('\n');
    }

    void raw(String line) {
        out.append(line).append('\n');
    }

    void append(Asm other) {
        out.append(other.out);
    }

    String text() {
        return out.toString();
    }
}
