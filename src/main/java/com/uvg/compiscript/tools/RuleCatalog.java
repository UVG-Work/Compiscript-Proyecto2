package com.uvg.compiscript.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Las reglas semanticas y las construcciones que se traducen a codigo
 * intermedio, numeradas para que cada caso de prueba declare cuales ejercita y
 * el arnes pueda demostrar cobertura.
 *
 * <p>Tres de ellas son <em>de capacidad</em>: describen algo que el lenguaje debe
 * soportar, no algo que un programa pueda violar. No tiene sentido pedirles un
 * caso fallido y por eso estan en {@link #CAPABILITY_ONLY}.
 */
public final class RuleCatalog {

    public static final Map<String, String> RULES = new LinkedHashMap<>();

    /** Reglas sin caso fallido posible: son soporte del lenguaje, no restricciones. */
    public static final Set<String> CAPABILITY_ONLY = Set.of("A5", "F3", "F4");

    static {
        RULES.put("T1", "aritmetica (+,-,*,/) sobre integer o float");
        RULES.put("T2", "operaciones logicas (&&,||,!) sobre boolean");
        RULES.put("T3", "compatibilidad en comparaciones (==,!=,<,<=,>,>=)");
        RULES.put("T4", "la asignacion coincide con el tipo declarado");
        RULES.put("T5", "inicializacion obligatoria de const");
        RULES.put("T6", "tipos en listas y estructuras");

        RULES.put("A1", "resolucion de nombres segun ambito local o global");
        RULES.put("A2", "error por uso de variables no declaradas");
        RULES.put("A3", "prohibicion de redeclaracion en el mismo ambito");
        RULES.put("A4", "acceso a variables en bloques anidados");
        RULES.put("A5", "un entorno nuevo por cada funcion, clase y bloque");

        RULES.put("F1", "numero y tipo de argumentos, por posicion");
        RULES.put("F2", "el retorno coincide con el tipo declarado");
        RULES.put("F3", "soporte para funciones recursivas");
        RULES.put("F4", "funciones anidadas y closures");
        RULES.put("F5", "redeclaracion de funciones con el mismo nombre");

        RULES.put("C1", "condiciones boolean en if, while, do-while, for y switch");
        RULES.put("C2", "break y continue solo dentro de bucles");
        RULES.put("C3", "return solo dentro del cuerpo de una funcion");

        RULES.put("K1", "existencia de atributos y metodos accedidos con '.'");
        RULES.put("K2", "correcta invocacion del constructor");
        RULES.put("K3", "manejo de 'this' dentro del ambito de la clase");
        RULES.put("K4", "un metodo sobrescrito conserva la firma del padre");
        RULES.put("K5", "un metodo solo se usa invocandolo, no como valor");
        RULES.put("K6", "una clase local no usa las locales de la funcion que la declara");

        RULES.put("L1", "tipo de los elementos de una lista");
        RULES.put("L2", "validacion de indices en el acceso a listas");

        RULES.put("G1", "deteccion de codigo muerto");
        RULES.put("G2", "sentido semantico en expresiones (no multiplicar funciones)");
        RULES.put("G3", "declaraciones duplicadas (variables, parametros)");
    }

    /** Lo que el enunciado pide traducir a codigo intermedio, mas lo que el lenguaje agrega. */
    public static final Map<String, String> GENERATION = new LinkedHashMap<>();

    static {
        GENERATION.put("I01", "aritmetica (+,-,*,/,%) con precedencia y asociatividad");
        GENERATION.put("I02", "logicas (&&, ||, !) con cortocircuito");
        GENERATION.put("I03", "comparaciones (==, !=, <, <=, >, >=)");
        GENERATION.put("I04", "declaracion y asignacion de variables y constantes");
        GENERATION.put("I05", "if/else");
        GENERATION.put("I06", "while");
        GENERATION.put("I07", "do-while");
        GENERATION.put("I08", "for");
        GENERATION.put("I09", "switch");
        GENERATION.put("I10", "break y continue hacia el bucle que los contiene");
        GENERATION.put("I11", "definicion de funciones, parametros, llamadas y retorno");
        GENERATION.put("I12", "funciones recursivas");
        GENERATION.put("I13", "funciones anidadas y closures (enlace de acceso)");
        GENERATION.put("I14", "creacion de objetos e invocacion del constructor");
        GENERATION.put("I15", "atributos, metodos y this");
        GENERATION.put("I16", "creacion de listas y acceso por indice");
        GENERATION.put("I17", "reciclaje de temporales");
        GENERATION.put("I18", "registros de activacion");
        GENERATION.put("I19", "herencia y despacho dinamico");
        GENERATION.put("I20", "foreach");
        GENERATION.put("I21", "try/catch");
        GENERATION.put("I22", "promocion de integer a float y concatenacion");
        GENERATION.put("I23", "operador ternario");
        GENERATION.put("I24", "funciones como valor (llamada indirecta)");
    }

    private RuleCatalog() {
    }

    public static boolean needsFailingCase(String rule) {
        return !CAPABILITY_ONLY.contains(rule);
    }
}
