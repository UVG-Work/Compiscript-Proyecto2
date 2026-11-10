package com.uvg.compiscript.mips;

/**
 * Rutinas de apoyo que se agregan a todo programa. Solo tocan {@code $a0-$a3},
 * {@code $v0}, {@code $t8} y {@code $t9}: llamarlas no obliga a guardar los
 * registros que getReg tiene ocupados.
 *
 * <p>Los manejadores de try viven en una pila propia: {@code try} apila la
 * direccion del catch con el {@code $sp} y el {@code $fp} de ese momento, y un
 * error en tiempo de ejecucion restaura ambos y salta ahi con el mensaje en
 * {@code $v0}. Sin manejador, el programa imprime el mensaje y termina con
 * codigo 1.
 */
final class MipsRuntime {

    private MipsRuntime() {
    }

    // ponytail: 1024 try anidados a la vez (recursion dentro de un try); sin verificacion de desborde.
    static final String DATA = """
                .align 2
            rt_htop:
                .word rt_handlers
            rt_handlers:
                .space 12288
            rt_true:
                .asciiz "true"
            rt_false:
                .asciiz "false"
            rt_null:
                .asciiz "null"
            rt_msg_uncaught:
                .asciiz "Error en tiempo de ejecucion: "
            rt_msg_div:
                .asciiz "division entre cero"
            rt_msg_index:
                .asciiz "indice fuera de rango"
            rt_msg_null:
                .asciiz "acceso a null"
            """;

    static final String CODE = """

            # ---------------------------------------------------------------
            # Runtime
            # ---------------------------------------------------------------

            # $a0 = cadena (o null)
            rt_print_str:
                bne $a0, $zero, rt_print_str_ok
                la $a0, rt_null
            rt_print_str_ok:
                li $v0, 4
                syscall
                jr $ra

            # $a0 = 0 o 1
            rt_print_bool:
                la $v0, rt_true
                bne $a0, $zero, rt_print_bool_ok
                la $v0, rt_false
            rt_print_bool_ok:
                move $a0, $v0
                li $v0, 4
                syscall
                jr $ra

            # $a0 = objeto o lista: null, o su direccion en hexadecimal
            rt_print_ref:
                bne $a0, $zero, rt_print_ref_ok
                la $a0, rt_null
                li $v0, 4
                syscall
                jr $ra
            rt_print_ref_ok:
                li $v0, 34
                syscall
                jr $ra

            # $v0 = cadena nueva en el heap con $a0 seguido de $a1
            rt_concat:
                bne $a0, $zero, rt_concat_a
                la $a0, rt_null
            rt_concat_a:
                bne $a1, $zero, rt_concat_b
                la $a1, rt_null
            rt_concat_b:
                li $a3, 1
                move $a2, $a0
            rt_concat_len1:
                lbu $t8, 0($a2)
                beq $t8, $zero, rt_concat_len2i
                addiu $a3, $a3, 1
                addiu $a2, $a2, 1
                j rt_concat_len1
            rt_concat_len2i:
                move $a2, $a1
            rt_concat_len2:
                lbu $t8, 0($a2)
                beq $t8, $zero, rt_concat_alloc
                addiu $a3, $a3, 1
                addiu $a2, $a2, 1
                j rt_concat_len2
            rt_concat_alloc:
                move $t9, $a0
                addiu $a0, $a3, 3
                srl $a0, $a0, 2
                sll $a0, $a0, 2
                li $v0, 9
                syscall
                move $a2, $v0
            rt_concat_copy1:
                lbu $t8, 0($t9)
                beq $t8, $zero, rt_concat_copy2
                sb $t8, 0($a2)
                addiu $t9, $t9, 1
                addiu $a2, $a2, 1
                j rt_concat_copy1
            rt_concat_copy2:
                lbu $t8, 0($a1)
                sb $t8, 0($a2)
                beq $t8, $zero, rt_concat_done
                addiu $a1, $a1, 1
                addiu $a2, $a2, 1
                j rt_concat_copy2
            rt_concat_done:
                jr $ra

            # $v0 = 1 si $a0 y $a1 tienen el mismo contenido
            rt_streq:
                beq $a0, $a1, rt_streq_yes
                beq $a0, $zero, rt_streq_no
                beq $a1, $zero, rt_streq_no
            rt_streq_loop:
                lbu $t8, 0($a0)
                lbu $t9, 0($a1)
                bne $t8, $t9, rt_streq_no
                beq $t8, $zero, rt_streq_yes
                addiu $a0, $a0, 1
                addiu $a1, $a1, 1
                j rt_streq_loop
            rt_streq_yes:
                li $v0, 1
                jr $ra
            rt_streq_no:
                li $v0, 0
                jr $ra

            rt_err_div:
                la $a0, rt_msg_div
                j rt_throw
            rt_err_index:
                la $a0, rt_msg_index
                j rt_throw
            rt_err_null:
                la $a0, rt_msg_null
                j rt_throw

            # $a0 = mensaje. Salta al catch mas reciente o termina el programa.
            rt_throw:
                lw $t8, rt_htop
                la $t9, rt_handlers
                beq $t8, $t9, rt_uncaught
                addiu $t8, $t8, -12
                sw $t8, rt_htop
                lw $t9, 0($t8)
                lw $sp, 4($t8)
                lw $fp, 8($t8)
                move $v0, $a0
                jr $t9
            rt_uncaught:
                move $t9, $a0
                la $a0, rt_msg_uncaught
                li $v0, 4
                syscall
                move $a0, $t9
                li $v0, 4
                syscall
                li $a0, 10
                li $v0, 11
                syscall
                li $a0, 1
                li $v0, 17
                syscall
            """;
}
