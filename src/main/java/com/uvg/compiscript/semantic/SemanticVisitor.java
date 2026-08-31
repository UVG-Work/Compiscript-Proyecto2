package com.uvg.compiscript.semantic;

import static com.uvg.compiscript.semantic.SemanticError.Category.AMBITO;
import static com.uvg.compiscript.semantic.SemanticError.Category.CLASES;
import static com.uvg.compiscript.semantic.SemanticError.Category.CONTROL;
import static com.uvg.compiscript.semantic.SemanticError.Category.FUNCIONES;
import static com.uvg.compiscript.semantic.SemanticError.Category.GENERAL;
import static com.uvg.compiscript.semantic.SemanticError.Category.LISTAS;
import static com.uvg.compiscript.semantic.SemanticError.Category.TIPOS;

import com.uvg.compiscript.parser.CompiscriptBaseVisitor;
import com.uvg.compiscript.parser.CompiscriptParser;
import com.uvg.compiscript.symbols.ClassSymbol;
import com.uvg.compiscript.symbols.FunctionSymbol;
import com.uvg.compiscript.symbols.ParameterSymbol;
import com.uvg.compiscript.symbols.Scope;
import com.uvg.compiscript.symbols.ScopeManager;
import com.uvg.compiscript.symbols.Symbol;
import com.uvg.compiscript.symbols.VariableSymbol;
import com.uvg.compiscript.types.ArrayType;
import com.uvg.compiscript.types.ClassType;
import com.uvg.compiscript.types.ErrorType;
import com.uvg.compiscript.types.FunctionType;
import com.uvg.compiscript.types.PrimitiveType;
import com.uvg.compiscript.types.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Recorrido unico con adelanto de declaraciones.
 *
 * <p>Cada {@code visitXxx} de expresion devuelve el tipo que sube al padre;
 * {@link ErrorType} absorbe operaciones para no encadenar mensajes derivados.
 *
 * <p>Antes de verificar una lista de sentencias se registran sus clases y
 * funciones ({@link #hoist}). Sin eso la recursion, la recursion mutua y los
 * atributos cuyo tipo se declara mas abajo darian falsos "no declarado".
 */
public class SemanticVisitor extends CompiscriptBaseVisitor<Type> {

    private static final Type ERROR = ErrorType.INSTANCE;

    private final ScopeManager scopes = new ScopeManager();
    private final ErrorReporter reporter = new ErrorReporter();

    private final Deque<FunctionSymbol> functions = new ArrayDeque<>();
    private final Deque<ClassSymbol> classes = new ArrayDeque<>();
    private final Map<ClassType, ClassSymbol> classSymbols = new HashMap<>();
    private int loopDepth;
    private int blockCounter;

    public ErrorReporter getReporter() {
        return reporter;
    }

    public ScopeManager getScopes() {
        return scopes;
    }

    // ------------------------------------------------------------------
    // Programa y listas de sentencias
    // ------------------------------------------------------------------

    @Override
    public Type visitProgram(CompiscriptParser.ProgramContext ctx) {
        visitStatementList(ctx.statement());
        reportUnused(scopes.getRoot());
        return PrimitiveType.VOID;
    }

    private void visitStatementList(List<CompiscriptParser.StatementContext> statements) {
        hoist(statements);
        boolean unreachable = false;
        for (CompiscriptParser.StatementContext statement : statements) {
            if (unreachable) {
                reporter.error(statement, GENERAL,
                        "codigo muerto: esta sentencia nunca se alcanza");
                unreachable = false;
            }
            visit(statement);
            if (terminatesFlow(statement)) {
                unreachable = true;
            }
        }
    }

    private static boolean terminatesFlow(CompiscriptParser.StatementContext statement) {
        return statement.returnStatement() != null
                || statement.breakStatement() != null
                || statement.continueStatement() != null;
    }

    // ------------------------------------------------------------------
    // Adelanto de declaraciones
    // ------------------------------------------------------------------

    /**
     * Tres pasos, y el orden importa: los cascarones de clase primero para que
     * los miembros puedan referirse a cualquier clase, y las firmas de funcion
     * al final porque sus parametros pueden ser de tipo clase.
     */
    private void hoist(List<CompiscriptParser.StatementContext> statements) {
        List<CompiscriptParser.ClassDeclarationContext> classDeclarations = new ArrayList<>();
        List<CompiscriptParser.FunctionDeclarationContext> functionDeclarations = new ArrayList<>();
        for (CompiscriptParser.StatementContext statement : statements) {
            if (statement.classDeclaration() != null) {
                classDeclarations.add(statement.classDeclaration());
            } else if (statement.functionDeclaration() != null) {
                functionDeclarations.add(statement.functionDeclaration());
            }
        }

        for (CompiscriptParser.ClassDeclarationContext ctx : classDeclarations) {
            declareClassShell(ctx);
        }
        for (CompiscriptParser.ClassDeclarationContext ctx : classDeclarations) {
            buildClassMembers(ctx);
        }
        for (CompiscriptParser.FunctionDeclarationContext ctx : functionDeclarations) {
            declareFunctionSignature(ctx, null);
        }
    }

    private void declareClassShell(CompiscriptParser.ClassDeclarationContext ctx) {
        String name = ctx.Identifier(0).getText();
        ClassSymbol symbol = new ClassSymbol(name, new ClassType(name),
                ctx.Identifier(0).getSymbol());
        if (!scopes.define(symbol)) {
            reporter.error(ctx, AMBITO, "'" + name + "' ya esta declarado en este ambito");
            return;
        }
        classSymbols.put(symbol.getClassType(), symbol);
        symbol.setMemberScope(new Scope("clase " + name, Scope.Kind.CLASS, scopes.current()));
    }

    private void buildClassMembers(CompiscriptParser.ClassDeclarationContext ctx) {
        String name = ctx.Identifier(0).getText();
        if (!(scopes.current().resolveLocal(name) instanceof ClassSymbol symbol)
                || symbol.getMemberScope() == null) {
            return;
        }

        if (ctx.Identifier().size() > 1) {
            String parentName = ctx.Identifier(1).getText();
            Symbol found = scopes.resolve(parentName);
            if (!(found instanceof ClassSymbol parent)) {
                reporter.error(ctx.Identifier(1).getSymbol(), CLASES,
                        "la superclase '" + parentName + "' no esta declarada");
            } else {
                symbol.setParent(parent);
                symbol.getClassType().setParent(parent.getClassType());
                if (TypeRules.hasInheritanceCycle(symbol.getClassType())) {
                    reporter.error(ctx.Identifier(1).getSymbol(), CLASES,
                            "herencia circular entre '" + name + "' y '" + parentName + "'");
                    symbol.setParent(null);
                    symbol.getClassType().setParent(null);
                }
            }
        }

        Scope saved = scopes.current();
        scopes.enter(symbol.getMemberScope());
        classes.push(symbol);
        for (CompiscriptParser.ClassMemberContext member : ctx.classMember()) {
            if (member.functionDeclaration() != null) {
                declareFunctionSignature(member.functionDeclaration(), symbol);
            } else if (member.variableDeclaration() != null) {
                declareField(member.variableDeclaration().Identifier(),
                        member.variableDeclaration().typeAnnotation(),
                        member.variableDeclaration(), false);
            } else if (member.constantDeclaration() != null) {
                declareField(member.constantDeclaration().Identifier(),
                        member.constantDeclaration().typeAnnotation(),
                        member.constantDeclaration(), true);
            }
        }
        classes.pop();
        scopes.enter(saved);
    }

    /**
     * Los atributos deben anotar su tipo: un metodo puede usarlos antes de que
     * aparezcan textualmente, asi que inferirlos exigiria una pasada mas.
     */
    private void declareField(TerminalNode identifier,
                              CompiscriptParser.TypeAnnotationContext annotation,
                              ParserRuleContext ctx, boolean constant) {
        String name = identifier.getText();
        Type type;
        if (annotation == null) {
            reporter.error(ctx, CLASES, "el atributo '" + name + "' debe declarar su tipo");
            type = ERROR;
        } else {
            type = resolveType(annotation.type());
        }
        VariableSymbol symbol = new VariableSymbol(name, type,
                identifier.getSymbol(), constant);
        symbol.setInitialized(true);
        symbol.setUsed(true);
        if (!scopes.define(symbol)) {
            reporter.error(ctx, CLASES, "el miembro '" + name + "' ya existe en la clase");
        }
    }

    private void declareFunctionSignature(CompiscriptParser.FunctionDeclarationContext ctx,
                                          ClassSymbol owner) {
        String name = ctx.Identifier().getText();
        FunctionSymbol symbol = new FunctionSymbol(name, ctx.Identifier().getSymbol());
        symbol.setOwner(owner);

        List<Type> parameterTypes = new ArrayList<>();
        if (ctx.parameters() != null) {
            int index = 0;
            for (CompiscriptParser.ParameterContext parameter : ctx.parameters().parameter()) {
                Type type = parameter.type() == null ? ERROR : resolveType(parameter.type());
                if (parameter.type() == null) {
                    reporter.error(parameter, FUNCIONES,
                            "el parametro '" + parameter.Identifier().getText()
                                    + "' debe declarar su tipo");
                }
                parameterTypes.add(type);
                symbol.addParameter(new ParameterSymbol(parameter.Identifier().getText(), type,
                        parameter.Identifier().getSymbol(), index++));
            }
        }

        Type returnType = ctx.type() == null ? PrimitiveType.VOID : resolveType(ctx.type());
        if (symbol.isConstructor()) {
            if (ctx.type() != null) {
                reporter.error(ctx, CLASES, "el constructor no declara tipo de retorno");
            }
            returnType = PrimitiveType.VOID;
        }
        symbol.setReturnType(returnType);
        symbol.setType(new FunctionType(parameterTypes, returnType));

        if (!scopes.define(symbol)) {
            reporter.error(ctx, owner == null ? AMBITO : CLASES,
                    "'" + name + "' ya esta declarado en este ambito");
        }
    }

    // ------------------------------------------------------------------
    // Declaraciones
    // ------------------------------------------------------------------

    @Override
    public Type visitVariableDeclaration(CompiscriptParser.VariableDeclarationContext ctx) {
        String name = ctx.Identifier().getText();
        Type declared = ctx.typeAnnotation() == null ? null
                : resolveType(ctx.typeAnnotation().type());
        Type initial = null;
        if (ctx.initializer() != null) {
            initial = visit(ctx.initializer().expression());
        }

        Type type = resolveDeclaredType(ctx, name, declared, initial);
        VariableSymbol symbol = new VariableSymbol(name, type,
                ctx.Identifier().getSymbol(), false);
        symbol.setInitialized(ctx.initializer() != null);
        if (ctx.initializer() != null) {
            symbol.setKnownLength(literalLength(ctx.initializer().expression()));
        }
        if (!scopes.define(symbol)) {
            reporter.error(ctx, AMBITO, "'" + name + "' ya esta declarado en este ambito");
        }
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitConstantDeclaration(CompiscriptParser.ConstantDeclarationContext ctx) {
        String name = ctx.Identifier().getText();
        Type declared = ctx.typeAnnotation() == null ? null
                : resolveType(ctx.typeAnnotation().type());
        Type initial = visit(ctx.expression());

        Type type = resolveDeclaredType(ctx, name, declared, initial);
        VariableSymbol symbol = new VariableSymbol(name, type,
                ctx.Identifier().getSymbol(), true);
        symbol.setInitialized(true);
        symbol.setKnownLength(literalLength(ctx.expression()));
        if (!scopes.define(symbol)) {
            reporter.error(ctx, AMBITO, "'" + name + "' ya esta declarado en este ambito");
        }
        return PrimitiveType.VOID;
    }

    private Type resolveDeclaredType(ParserRuleContext ctx, String name,
                                     Type declared, Type initial) {
        if (declared == null && initial == null) {
            reporter.error(ctx, TIPOS, "no se puede inferir el tipo de '" + name
                    + "': falta la anotacion o el valor inicial");
            return ERROR;
        }
        if (declared == null) {
            if (TypeRules.isEmptyArrayLiteral(initial)) {
                reporter.error(ctx, LISTAS, "no se puede inferir el tipo de '" + name
                        + "' desde una lista vacia: anote el tipo");
                return ERROR;
            }
            return initial;
        }
        if (initial != null && !TypeRules.assignable(declared, initial)) {
            reporter.error(ctx, TIPOS, "no se puede inicializar '" + name + "' de tipo "
                    + declared.getName() + " con un valor " + initial.getName());
        }
        return declared;
    }

    // ------------------------------------------------------------------
    // Funciones
    // ------------------------------------------------------------------

    @Override
    public Type visitFunctionDeclaration(CompiscriptParser.FunctionDeclarationContext ctx) {
        // El simbolo ya lo creo hoist(); aqui solo se verifica el cuerpo.
        if (scopes.current().resolveLocal(ctx.Identifier().getText())
                instanceof FunctionSymbol symbol) {
            checkFunctionBody(ctx, symbol);
        }
        return PrimitiveType.VOID;
    }

    private void checkFunctionBody(CompiscriptParser.FunctionDeclarationContext ctx,
                                   FunctionSymbol symbol) {
        Scope body = scopes.push("funcion " + symbol.getName(), Scope.Kind.FUNCTION);
        symbol.setBodyScope(body);

        // Se definen copias: `define` asigna el offset, y una funcion declarada
        // dos veces reutilizaria el mismo objeto en dos ambitos distintos.
        List<ParameterSymbol> declared = new ArrayList<>();
        for (ParameterSymbol parameter : symbol.getParameters()) {
            ParameterSymbol copy = new ParameterSymbol(parameter.getName(), parameter.getType(),
                    parameter.getDeclaration(), parameter.getIndex());
            if (scopes.define(copy)) {
                declared.add(copy);
            } else {
                reporter.error(ctx, FUNCIONES,
                        "el parametro '" + parameter.getName() + "' esta repetido");
                declared.add(parameter);
            }
        }
        symbol.getParameters().clear();
        symbol.getParameters().addAll(declared);

        functions.push(symbol);
        // Una funcion anidada dentro de un bucle no puede romper ese bucle.
        int savedLoop = loopDepth;
        loopDepth = 0;
        visitStatementList(ctx.block().statement());
        loopDepth = savedLoop;
        functions.pop();

        Type returnType = symbol.getReturnType();
        if (returnType != PrimitiveType.VOID && !TypeRules.isError(returnType)
                && !alwaysReturns(ctx.block().statement())) {
            reporter.error(ctx, FUNCIONES, "la funcion '" + symbol.getName()
                    + "' declara retorno " + returnType.getName()
                    + " pero hay caminos que no retornan");
        }
        reportUnused(body);
        scopes.pop();
    }

    /**
     * Conservador a proposito: solo cuenta un {@code return}, un bloque que
     * retorna, o un {@code if/else} donde retornan las dos ramas. Preferimos el
     * falso positivo a dejar pasar una funcion que de verdad no retorna.
     */
    private boolean alwaysReturns(List<CompiscriptParser.StatementContext> statements) {
        for (CompiscriptParser.StatementContext statement : statements) {
            if (statementReturns(statement)) {
                return true;
            }
        }
        return false;
    }

    private boolean statementReturns(CompiscriptParser.StatementContext statement) {
        if (statement.returnStatement() != null) {
            return true;
        }
        if (statement.block() != null) {
            return alwaysReturns(statement.block().statement());
        }
        if (statement.ifStatement() != null) {
            CompiscriptParser.IfStatementContext ifStatement = statement.ifStatement();
            return ifStatement.block().size() == 2
                    && alwaysReturns(ifStatement.block(0).statement())
                    && alwaysReturns(ifStatement.block(1).statement());
        }
        return false;
    }

    @Override
    public Type visitReturnStatement(CompiscriptParser.ReturnStatementContext ctx) {
        Type value = ctx.expression() == null ? PrimitiveType.VOID : visit(ctx.expression());
        if (functions.isEmpty()) {
            reporter.error(ctx, CONTROL, "'return' fuera de una funcion");
            return PrimitiveType.VOID;
        }
        FunctionSymbol function = functions.peek();
        Type expected = function.getReturnType();
        if (expected == PrimitiveType.VOID) {
            if (ctx.expression() != null) {
                reporter.error(ctx, FUNCIONES, "la funcion '" + function.getName()
                        + "' no declara tipo de retorno y no puede devolver un valor");
            }
        } else if (ctx.expression() == null) {
            reporter.error(ctx, FUNCIONES, "la funcion '" + function.getName()
                    + "' debe devolver un valor de tipo " + expected.getName());
        } else if (!TypeRules.assignable(expected, value)) {
            reporter.error(ctx, FUNCIONES, "la funcion '" + function.getName()
                    + "' devuelve " + expected.getName() + ", no " + value.getName());
        }
        return PrimitiveType.VOID;
    }

    // ------------------------------------------------------------------
    // Clases
    // ------------------------------------------------------------------

    @Override
    public Type visitClassDeclaration(CompiscriptParser.ClassDeclarationContext ctx) {
        if (!(scopes.current().resolveLocal(ctx.Identifier(0).getText())
                instanceof ClassSymbol symbol) || symbol.getMemberScope() == null) {
            return PrimitiveType.VOID;
        }

        Scope saved = scopes.current();
        scopes.enter(symbol.getMemberScope());
        classes.push(symbol);

        // Primero los inicializadores de atributos: un metodo puede usar
        // cualquier atributo, asi que todos deben estar tipados antes.
        for (CompiscriptParser.ClassMemberContext member : ctx.classMember()) {
            if (member.variableDeclaration() != null
                    && member.variableDeclaration().initializer() != null) {
                checkFieldInitializer(member.variableDeclaration().Identifier().getText(),
                        member.variableDeclaration().initializer().expression(),
                        member.variableDeclaration());
            } else if (member.constantDeclaration() != null) {
                checkFieldInitializer(member.constantDeclaration().Identifier().getText(),
                        member.constantDeclaration().expression(),
                        member.constantDeclaration());
            }
        }
        for (CompiscriptParser.ClassMemberContext member : ctx.classMember()) {
            if (member.functionDeclaration() != null
                    && scopes.current().resolveLocal(
                            member.functionDeclaration().Identifier().getText())
                    instanceof FunctionSymbol method) {
                checkFunctionBody(member.functionDeclaration(), method);
            }
        }

        classes.pop();
        scopes.enter(saved);
        return PrimitiveType.VOID;
    }

    private void checkFieldInitializer(String name, CompiscriptParser.ExpressionContext expression,
                                       ParserRuleContext ctx) {
        Type value = visit(expression);
        Symbol symbol = scopes.current().resolveLocal(name);
        if (symbol != null && !TypeRules.assignable(symbol.getType(), value)) {
            reporter.error(ctx, TIPOS, "no se puede inicializar el atributo '" + name
                    + "' de tipo " + symbol.getType().getName()
                    + " con un valor " + value.getName());
        }
    }

    @Override
    public Type visitThisExpr(CompiscriptParser.ThisExprContext ctx) {
        if (classes.isEmpty()) {
            reporter.error(ctx, CLASES, "'this' solo puede usarse dentro de una clase");
            return ERROR;
        }
        return classes.peek().getClassType();
    }

    @Override
    public Type visitNewExpr(CompiscriptParser.NewExprContext ctx) {
        String name = ctx.Identifier().getText();
        Symbol symbol = scopes.resolve(name);
        if (!(symbol instanceof ClassSymbol classSymbol)) {
            reporter.error(ctx, CLASES, "la clase '" + name + "' no esta declarada");
            visitArgumentTypes(ctx.arguments());
            return ERROR;
        }

        List<Type> arguments = visitArgumentTypes(ctx.arguments());
        Symbol constructor = classSymbol.resolveMember("constructor");
        if (constructor instanceof FunctionSymbol function
                && function.getType() instanceof FunctionType signature) {
            checkArguments(ctx, "constructor de " + name, signature, arguments);
        } else if (!arguments.isEmpty()) {
            reporter.error(ctx, CLASES, "la clase '" + name
                    + "' no declara constructor y no admite argumentos");
        }
        return classSymbol.getClassType();
    }

    private List<Type> visitArgumentTypes(CompiscriptParser.ArgumentsContext ctx) {
        List<Type> types = new ArrayList<>();
        if (ctx != null) {
            for (CompiscriptParser.ExpressionContext expression : ctx.expression()) {
                types.add(visit(expression));
            }
        }
        return types;
    }

    private void checkArguments(ParserRuleContext ctx, String what,
                                FunctionType signature, List<Type> arguments) {
        if (signature.getArity() != arguments.size()) {
            reporter.error(ctx, FUNCIONES, what + " espera " + signature.getArity()
                    + " argumento(s) y recibio " + arguments.size());
            return;
        }
        for (int i = 0; i < arguments.size(); i++) {
            Type expected = signature.getParameterTypes().get(i);
            Type actual = arguments.get(i);
            if (!TypeRules.assignable(expected, actual)) {
                reporter.error(ctx, FUNCIONES, what + ": el argumento " + (i + 1)
                        + " es " + actual.getName() + " y se esperaba " + expected.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // Ambitos y control de flujo
    // ------------------------------------------------------------------

    @Override
    public Type visitBlock(CompiscriptParser.BlockContext ctx) {
        Scope scope = scopes.push("bloque#" + (++blockCounter), Scope.Kind.BLOCK);
        visitStatementList(ctx.statement());
        reportUnused(scope);
        scopes.pop();
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitIfStatement(CompiscriptParser.IfStatementContext ctx) {
        checkCondition(ctx.expression(), "if");
        for (CompiscriptParser.BlockContext block : ctx.block()) {
            visit(block);
        }
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitWhileStatement(CompiscriptParser.WhileStatementContext ctx) {
        checkCondition(ctx.expression(), "while");
        loopDepth++;
        visit(ctx.block());
        loopDepth--;
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitDoWhileStatement(CompiscriptParser.DoWhileStatementContext ctx) {
        loopDepth++;
        visit(ctx.block());
        loopDepth--;
        checkCondition(ctx.expression(), "do-while");
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitForStatement(CompiscriptParser.ForStatementContext ctx) {
        Scope scope = scopes.push("bloque#" + (++blockCounter), Scope.Kind.BLOCK);
        if (ctx.variableDeclaration() != null) {
            visit(ctx.variableDeclaration());
        } else if (ctx.assignment() != null) {
            visit(ctx.assignment());
        }

        CompiscriptParser.ExpressionContext condition = forCondition(ctx);
        if (condition != null) {
            checkCondition(condition, "for");
        }
        loopDepth++;
        visit(ctx.block());
        loopDepth--;
        CompiscriptParser.ExpressionContext update = forUpdate(ctx);
        if (update != null) {
            visit(update);
        }
        reportUnused(scope);
        scopes.pop();
        return PrimitiveType.VOID;
    }

    /**
     * {@code ctx.expression()} es una lista y el accesor no dice si el unico
     * elemento presente es la condicion o el incremento. Se decide por la
     * posicion respecto al {@code ;} separador: si el inicializador es una regla
     * se come su propio {@code ;} y no es hijo directo.
     */
    private CompiscriptParser.ExpressionContext forCondition(
            CompiscriptParser.ForStatementContext ctx) {
        return forExpressionAt(ctx, true);
    }

    private CompiscriptParser.ExpressionContext forUpdate(
            CompiscriptParser.ForStatementContext ctx) {
        return forExpressionAt(ctx, false);
    }

    private CompiscriptParser.ExpressionContext forExpressionAt(
            CompiscriptParser.ForStatementContext ctx, boolean wantCondition) {
        int separator = (ctx.variableDeclaration() != null || ctx.assignment() != null) ? 0 : 1;
        int seen = 0;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode terminal && ";".equals(terminal.getText())) {
                seen++;
            } else if (child instanceof CompiscriptParser.ExpressionContext expression) {
                boolean isCondition = seen <= separator;
                if (isCondition == wantCondition) {
                    return expression;
                }
            }
        }
        return null;
    }

    @Override
    public Type visitForeachStatement(CompiscriptParser.ForeachStatementContext ctx) {
        Type iterable = visit(ctx.expression());
        Type element = ERROR;
        if (iterable instanceof ArrayType array) {
            element = array.getElementType();
        } else if (!TypeRules.isError(iterable)) {
            reporter.error(ctx.expression(), LISTAS,
                    "'foreach' necesita una lista, no " + iterable.getName());
        }

        Scope scope = scopes.push("bloque#" + (++blockCounter), Scope.Kind.BLOCK);
        VariableSymbol loopVariable = new VariableSymbol(ctx.Identifier().getText(), element,
                ctx.Identifier().getSymbol(), false);
        loopVariable.setInitialized(true);
        scopes.define(loopVariable);
        loopDepth++;
        visitStatementList(ctx.block().statement());
        loopDepth--;
        reportUnused(scope);
        scopes.pop();
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitBreakStatement(CompiscriptParser.BreakStatementContext ctx) {
        if (loopDepth == 0) {
            reporter.error(ctx, CONTROL, "'break' solo puede usarse dentro de un bucle");
        }
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitContinueStatement(CompiscriptParser.ContinueStatementContext ctx) {
        if (loopDepth == 0) {
            reporter.error(ctx, CONTROL, "'continue' solo puede usarse dentro de un bucle");
        }
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitSwitchStatement(CompiscriptParser.SwitchStatementContext ctx) {
        Type selector = visit(ctx.expression());
        requireBoolean(selector, ctx.expression(), "switch");
        for (CompiscriptParser.SwitchCaseContext switchCase : ctx.switchCase()) {
            Type label = visit(switchCase.expression());
            if (!TypeRules.comparable(selector, label)) {
                reporter.error(switchCase.expression(), TIPOS, "el 'case' es "
                        + label.getName() + " y el selector del 'switch' es "
                        + selector.getName());
            }
            Scope scope = scopes.push("bloque#" + (++blockCounter), Scope.Kind.BLOCK);
            visitStatementList(switchCase.statement());
            reportUnused(scope);
            scopes.pop();
        }
        if (ctx.defaultCase() != null) {
            Scope scope = scopes.push("bloque#" + (++blockCounter), Scope.Kind.BLOCK);
            visitStatementList(ctx.defaultCase().statement());
            reportUnused(scope);
            scopes.pop();
        }
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitTryCatchStatement(CompiscriptParser.TryCatchStatementContext ctx) {
        visit(ctx.block(0));

        Scope scope = scopes.push("bloque#" + (++blockCounter), Scope.Kind.BLOCK);
        VariableSymbol error = new VariableSymbol(ctx.Identifier().getText(),
                PrimitiveType.STRING, ctx.Identifier().getSymbol(), false);
        error.setInitialized(true);
        scopes.define(error);
        visitStatementList(ctx.block(1).statement());
        reportUnused(scope);
        scopes.pop();
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitPrintStatement(CompiscriptParser.PrintStatementContext ctx) {
        visit(ctx.expression());
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitExpressionStatement(CompiscriptParser.ExpressionStatementContext ctx) {
        visit(ctx.expression());
        return PrimitiveType.VOID;
    }

    private void checkCondition(CompiscriptParser.ExpressionContext ctx, String where) {
        requireBoolean(visit(ctx), ctx, where);
    }

    private void requireBoolean(Type type, ParserRuleContext ctx, String where) {
        if (!TypeRules.isError(type) && type != PrimitiveType.BOOLEAN) {
            reporter.error(ctx, TIPOS,
                    "la condicion de '" + where + "' debe ser boolean, no " + type.getName());
        }
    }

    // ------------------------------------------------------------------
    // Asignaciones
    // ------------------------------------------------------------------

    @Override
    public Type visitAssignment(CompiscriptParser.AssignmentContext ctx) {
        if (ctx.expression().size() == 1) {
            Type value = visit(ctx.expression(0));
            assignToName(ctx.Identifier().getSymbol(), value, ctx, ctx.expression(0));
        } else {
            Type object = visit(ctx.expression(0));
            Type value = visit(ctx.expression(1));
            assignToProperty(object, ctx.Identifier().getSymbol(), value, ctx);
        }
        return PrimitiveType.VOID;
    }

    // `x = 1;` deriva por `assignment` y tambien por `expressionStatement`.
    // ANTLR se queda con la primera alternativa, pero la forma de expresion
    // aparece igual dentro de otras expresiones y hay que verificarla.
    @Override
    public Type visitAssignExpr(CompiscriptParser.AssignExprContext ctx) {
        Type value = visit(ctx.assignmentExpr());
        return assignToLeftHandSide(ctx.lhs, value, ctx);
    }

    @Override
    public Type visitPropertyAssignExpr(CompiscriptParser.PropertyAssignExprContext ctx) {
        Type object = visitLeftHandSide(ctx.lhs);
        Type value = visit(ctx.assignmentExpr());
        return assignToProperty(object, ctx.Identifier().getSymbol(), value, ctx);
    }

    private Type assignToLeftHandSide(CompiscriptParser.LeftHandSideContext lhs, Type value,
                                      ParserRuleContext ctx) {
        int count = lhs.suffixOp().size();
        if (count == 0) {
            if (lhs.primaryAtom() instanceof CompiscriptParser.IdentifierExprContext identifier) {
                assignToName(identifier.Identifier().getSymbol(), value, ctx, null);
                Symbol symbol = scopes.resolve(identifier.Identifier().getText());
                return symbol == null || symbol.getType() == null ? ERROR : symbol.getType();
            }
            visit(lhs);
            reporter.error(ctx, GENERAL, "el destino de la asignacion no es asignable");
            return ERROR;
        }

        CompiscriptParser.SuffixOpContext last = lhs.suffixOp(count - 1);
        if (last instanceof CompiscriptParser.PropertyAccessExprContext property) {
            Type object = foldSuffixes(lhs, count - 1);
            return assignToProperty(object, property.Identifier().getSymbol(), value, ctx);
        }
        if (last instanceof CompiscriptParser.IndexExprContext index) {
            Type array = foldSuffixes(lhs, count - 1);
            Type element = indexInto(array, index, lhs, count - 1);
            if (!TypeRules.assignable(element, value)) {
                reporter.error(ctx, TIPOS, "no se puede guardar un valor " + value.getName()
                        + " en una lista de " + element.getName());
            }
            return element;
        }
        foldSuffixes(lhs, count);
        reporter.error(ctx, GENERAL, "no se puede asignar al resultado de una llamada");
        return ERROR;
    }

    private void assignToName(Token token, Type value, ParserRuleContext ctx,
                              CompiscriptParser.ExpressionContext valueExpression) {
        String name = token.getText();
        Symbol symbol = scopes.resolve(name);
        if (symbol == null) {
            reporter.error(token, AMBITO, "'" + name + "' no esta declarado");
            return;
        }
        if (!(symbol instanceof VariableSymbol) && !(symbol instanceof ParameterSymbol)) {
            reporter.error(token, AMBITO,
                    "'" + name + "' no es una variable y no se puede asignar");
            return;
        }
        if (symbol instanceof VariableSymbol variable && variable.isConstant()) {
            reporter.error(token, TIPOS, "no se puede reasignar la constante '" + name + "'");
            return;
        }
        if (!TypeRules.assignable(symbol.getType(), value)) {
            reporter.error(ctx, TIPOS, "no se puede asignar un valor " + value.getName()
                    + " a '" + name + "' de tipo " + symbol.getType().getName());
        }
        if (symbol instanceof VariableSymbol variable) {
            variable.setInitialized(true);
            // Cualquier reasignacion invalida la longitud conocida.
            variable.setKnownLength(valueExpression == null ? -1 : literalLength(valueExpression));
        }
        noteCapture(name);
    }

    private Type assignToProperty(Type object, Token token, Type value, ParserRuleContext ctx) {
        String name = token.getText();
        if (TypeRules.isError(object)) {
            return ERROR;
        }
        if (!(object instanceof ClassType classType)) {
            reporter.error(token, CLASES,
                    "un valor de tipo " + object.getName() + " no tiene propiedades");
            return ERROR;
        }
        ClassSymbol owner = classSymbols.get(classType);
        Symbol member = owner == null ? null : owner.resolveMember(name);
        if (member == null) {
            reporter.error(token, CLASES, "la clase '" + classType.getName()
                    + "' no tiene el miembro '" + name + "'");
            return ERROR;
        }
        if (member instanceof VariableSymbol field && field.isConstant()) {
            reporter.error(token, TIPOS,
                    "no se puede reasignar el atributo constante '" + name + "'");
            return member.getType();
        }
        if (!TypeRules.assignable(member.getType(), value)) {
            reporter.error(ctx, TIPOS, "no se puede asignar un valor " + value.getName()
                    + " al atributo '" + name + "' de tipo " + member.getType().getName());
        }
        return member.getType();
    }

    // ------------------------------------------------------------------
    // Expresiones
    // ------------------------------------------------------------------

    @Override
    public Type visitExpression(CompiscriptParser.ExpressionContext ctx) {
        return visit(ctx.assignmentExpr());
    }

    @Override
    public Type visitExprNoAssign(CompiscriptParser.ExprNoAssignContext ctx) {
        return visit(ctx.conditionalExpr());
    }

    @Override
    public Type visitTernaryExpr(CompiscriptParser.TernaryExprContext ctx) {
        Type condition = visit(ctx.logicalOrExpr());
        if (ctx.expression().isEmpty()) {
            return condition;
        }
        if (!TypeRules.isError(condition) && condition != PrimitiveType.BOOLEAN) {
            reporter.error(ctx.logicalOrExpr(), TIPOS,
                    "la condicion del operador ternario debe ser boolean, no "
                            + condition.getName());
        }
        Type left = visit(ctx.expression(0));
        Type right = visit(ctx.expression(1));
        Type common = TypeRules.unify(left, right);
        if (common == null) {
            reporter.error(ctx, TIPOS, "las ramas del operador ternario no tienen un tipo comun: "
                    + left.getName() + " y " + right.getName());
            return ERROR;
        }
        return common;
    }

    @Override
    public Type visitLogicalOrExpr(CompiscriptParser.LogicalOrExprContext ctx) {
        return checkBooleanChain(ctx.logicalAndExpr());
    }

    @Override
    public Type visitLogicalAndExpr(CompiscriptParser.LogicalAndExprContext ctx) {
        return checkBooleanChain(ctx.equalityExpr());
    }

    private Type checkBooleanChain(List<? extends ParserRuleContext> parts) {
        if (parts.size() == 1) {
            return visit(parts.get(0));
        }
        for (ParserRuleContext part : parts) {
            Type type = visit(part);
            if (!TypeRules.isError(type) && type != PrimitiveType.BOOLEAN) {
                reporter.error(part, TIPOS,
                        "los operadores logicos solo aceptan boolean, no " + type.getName());
            }
        }
        return PrimitiveType.BOOLEAN;
    }

    @Override
    public Type visitEqualityExpr(CompiscriptParser.EqualityExprContext ctx) {
        List<CompiscriptParser.RelationalExprContext> parts = ctx.relationalExpr();
        if (parts.size() == 1) {
            return visit(parts.get(0));
        }
        Type left = visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            Type right = visit(parts.get(i));
            Token operator = operatorAt(ctx, 2 * i - 1);
            if (!TypeRules.comparable(left, right)) {
                reporter.error(operator, TIPOS, "no se puede aplicar '" + operator.getText()
                        + "' a " + left.getName() + " y " + right.getName());
            }
            left = PrimitiveType.BOOLEAN;
        }
        return PrimitiveType.BOOLEAN;
    }

    @Override
    public Type visitRelationalExpr(CompiscriptParser.RelationalExprContext ctx) {
        List<CompiscriptParser.AdditiveExprContext> parts = ctx.additiveExpr();
        if (parts.size() == 1) {
            return visit(parts.get(0));
        }
        Type left = visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            Type right = visit(parts.get(i));
            Token operator = operatorAt(ctx, 2 * i - 1);
            if (!TypeRules.isError(left) && !TypeRules.isError(right)
                    && !(isNumeric(left) && isNumeric(right))) {
                reporter.error(operator, TIPOS, "no se puede aplicar '" + operator.getText()
                        + "' a " + left.getName() + " y " + right.getName());
            }
            left = PrimitiveType.BOOLEAN;
        }
        return PrimitiveType.BOOLEAN;
    }

    @Override
    public Type visitAdditiveExpr(CompiscriptParser.AdditiveExprContext ctx) {
        List<CompiscriptParser.MultiplicativeExprContext> parts = ctx.multiplicativeExpr();
        if (parts.size() == 1) {
            return visit(parts.get(0));
        }
        Type left = visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            Type right = visit(parts.get(i));
            left = additive(operatorAt(ctx, 2 * i - 1), left, right);
        }
        return left;
    }

    private Type additive(Token operator, Type left, Type right) {
        if (TypeRules.isError(left) || TypeRules.isError(right)) {
            return ERROR;
        }
        boolean plus = "+".equals(operator.getText());
        if (plus && left == PrimitiveType.STRING && right == PrimitiveType.STRING) {
            return PrimitiveType.STRING;
        }
        if (isNumeric(left) && isNumeric(right)) {
            return left == PrimitiveType.FLOAT || right == PrimitiveType.FLOAT
                    ? PrimitiveType.FLOAT : PrimitiveType.INTEGER;
        }
        reporter.error(operator, TIPOS, "no se puede aplicar '" + operator.getText()
                + "' a " + left.getName() + " y " + right.getName());
        return ERROR;
    }

    @Override
    public Type visitMultiplicativeExpr(CompiscriptParser.MultiplicativeExprContext ctx) {
        List<CompiscriptParser.UnaryExprContext> parts = ctx.unaryExpr();
        if (parts.size() == 1) {
            return visit(parts.get(0));
        }
        Type left = visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            Type right = visit(parts.get(i));
            Token operator = operatorAt(ctx, 2 * i - 1);
            if (TypeRules.isError(left) || TypeRules.isError(right)) {
                left = ERROR;
            } else if (!isNumeric(left) || !isNumeric(right)) {
                reporter.error(operator, TIPOS, "no se puede aplicar '" + operator.getText()
                        + "' a " + left.getName() + " y " + right.getName());
                left = ERROR;
            } else if ("%".equals(operator.getText())
                    && (left != PrimitiveType.INTEGER || right != PrimitiveType.INTEGER)) {
                reporter.error(operator, TIPOS, "el operador '%' solo acepta integer");
                left = ERROR;
            } else {
                left = left == PrimitiveType.FLOAT || right == PrimitiveType.FLOAT
                        ? PrimitiveType.FLOAT : PrimitiveType.INTEGER;
            }
        }
        return left;
    }

    @Override
    public Type visitUnaryExpr(CompiscriptParser.UnaryExprContext ctx) {
        if (ctx.primaryExpr() != null) {
            return visit(ctx.primaryExpr());
        }
        Type operand = visit(ctx.unaryExpr());
        Token operator = operatorAt(ctx, 0);
        if (TypeRules.isError(operand)) {
            return ERROR;
        }
        if ("!".equals(operator.getText())) {
            if (operand != PrimitiveType.BOOLEAN) {
                reporter.error(operator, TIPOS, "no se puede aplicar '!' a " + operand.getName());
                return ERROR;
            }
            return PrimitiveType.BOOLEAN;
        }
        if (!isNumeric(operand)) {
            reporter.error(operator, TIPOS, "no se puede aplicar '-' a " + operand.getName());
            return ERROR;
        }
        return operand;
    }

    @Override
    public Type visitPrimaryExpr(CompiscriptParser.PrimaryExprContext ctx) {
        if (ctx.literalExpr() != null) {
            return visit(ctx.literalExpr());
        }
        if (ctx.leftHandSide() != null) {
            return visit(ctx.leftHandSide());
        }
        return visit(ctx.expression());
    }

    // El token `Literal` engulle a FloatLiteral, IntegerLiteral y StringLiteral
    // porque esta declarado antes, asi que hay que distinguirlos por el texto.
    @Override
    public Type visitLiteralExpr(CompiscriptParser.LiteralExprContext ctx) {
        if (ctx.Literal() != null) {
            String text = ctx.Literal().getText();
            if (text.startsWith("\"")) {
                return PrimitiveType.STRING;
            }
            return text.indexOf('.') >= 0 ? PrimitiveType.FLOAT : PrimitiveType.INTEGER;
        }
        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }
        return "null".equals(ctx.getText()) ? PrimitiveType.NULL : PrimitiveType.BOOLEAN;
    }

    @Override
    public Type visitArrayLiteral(CompiscriptParser.ArrayLiteralContext ctx) {
        if (ctx.expression().isEmpty()) {
            return TypeRules.EMPTY_ARRAY;
        }
        Type element = visit(ctx.expression(0));
        for (int i = 1; i < ctx.expression().size(); i++) {
            Type next = visit(ctx.expression(i));
            Type common = TypeRules.unify(element, next);
            if (common == null) {
                reporter.error(ctx.expression(i), LISTAS, "la lista debe ser homogenea: "
                        + element.getName() + " no convive con " + next.getName());
                return new ArrayType(ERROR);
            }
            element = common;
        }
        return new ArrayType(element);
    }

    // ------------------------------------------------------------------
    // leftHandSide: primaryAtom (suffixOp)*
    // ------------------------------------------------------------------

    @Override
    public Type visitLeftHandSide(CompiscriptParser.LeftHandSideContext ctx) {
        return foldSuffixes(ctx, ctx.suffixOp().size());
    }

    /**
     * Aplica los primeros {@code count} sufijos. Asignar a {@code obj.campo}
     * necesita el tipo del objeto, o sea el resultado de aplicar todos menos el
     * ultimo.
     */
    private Type foldSuffixes(CompiscriptParser.LeftHandSideContext ctx, int count) {
        Type current = visit(ctx.primaryAtom());
        for (int i = 0; i < count; i++) {
            CompiscriptParser.SuffixOpContext suffix = ctx.suffixOp(i);
            if (suffix instanceof CompiscriptParser.CallExprContext call) {
                current = callInto(current, call, ctx, i);
            } else if (suffix instanceof CompiscriptParser.IndexExprContext index) {
                current = indexInto(current, index, ctx, i);
            } else if (suffix instanceof CompiscriptParser.PropertyAccessExprContext property) {
                current = propertyInto(current, property);
            }
        }
        return current;
    }

    private Type callInto(Type callee, CompiscriptParser.CallExprContext call,
                          CompiscriptParser.LeftHandSideContext ctx, int position) {
        List<Type> arguments = visitArgumentTypes(call.arguments());
        if (TypeRules.isError(callee)) {
            return ERROR;
        }
        if (!(callee instanceof FunctionType signature)) {
            reporter.error(call, FUNCIONES, "'" + calleeName(ctx, position)
                    + "' no es una funcion: es " + callee.getName());
            return ERROR;
        }
        checkArguments(call, "'" + calleeName(ctx, position) + "'", signature, arguments);
        return signature.getReturnType();
    }

    private String calleeName(CompiscriptParser.LeftHandSideContext ctx, int position) {
        StringBuilder name = new StringBuilder(ctx.primaryAtom().getText());
        for (int i = 0; i < position; i++) {
            name.append(ctx.suffixOp(i).getText());
        }
        return name.toString();
    }

    private Type indexInto(Type target, CompiscriptParser.IndexExprContext index,
                           CompiscriptParser.LeftHandSideContext ctx, int position) {
        Type indexType = visit(index.expression());
        if (!TypeRules.isError(indexType) && indexType != PrimitiveType.INTEGER) {
            reporter.error(index.expression(), LISTAS,
                    "el indice de una lista debe ser integer, no " + indexType.getName());
        }
        if (TypeRules.isError(target)) {
            return ERROR;
        }
        if (!(target instanceof ArrayType array)) {
            reporter.error(index, LISTAS,
                    "no se puede indexar un valor de tipo " + target.getName());
            return ERROR;
        }
        checkIndexRange(ctx, position, index);
        return array.getElementType();
    }

    /**
     * Rango solo cuando se conoce: la variable se inicializo con un literal de
     * lista y el indice es una constante. Es analisis lineal, no vale dentro de
     * un bucle.
     */
    private void checkIndexRange(CompiscriptParser.LeftHandSideContext ctx, int position,
                                 CompiscriptParser.IndexExprContext index) {
        if (position != 0
                || !(ctx.primaryAtom() instanceof CompiscriptParser.IdentifierExprContext name)
                || !(scopes.resolve(name.Identifier().getText()) instanceof VariableSymbol array)
                || array.getKnownLength() < 0) {
            return;
        }
        int value = literalInteger(index.expression());
        if (value >= array.getKnownLength()) {
            reporter.error(index, LISTAS, "indice fuera de rango: '" + array.getName()
                    + "' tiene " + array.getKnownLength() + " elemento(s)");
        }
    }

    private Type propertyInto(Type target,
                              CompiscriptParser.PropertyAccessExprContext property) {
        String name = property.Identifier().getText();
        if (TypeRules.isError(target)) {
            return ERROR;
        }
        if (target instanceof ArrayType) {
            if ("length".equals(name)) {
                return PrimitiveType.INTEGER;
            }
            reporter.error(property, LISTAS,
                    "las listas solo exponen la propiedad 'length', no '" + name + "'");
            return ERROR;
        }
        if (!(target instanceof ClassType classType)) {
            reporter.error(property, CLASES,
                    "un valor de tipo " + target.getName() + " no tiene propiedades");
            return ERROR;
        }
        ClassSymbol owner = classSymbols.get(classType);
        Symbol member = owner == null ? null : owner.resolveMember(name);
        if (member == null) {
            reporter.error(property, CLASES, "la clase '" + classType.getName()
                    + "' no tiene el miembro '" + name + "'");
            return ERROR;
        }
        return member.getType() == null ? ERROR : member.getType();
    }

    @Override
    public Type visitIdentifierExpr(CompiscriptParser.IdentifierExprContext ctx) {
        String name = ctx.Identifier().getText();
        Symbol symbol = scopes.resolve(name);
        if (symbol == null) {
            reporter.error(ctx, AMBITO, "'" + name + "' no esta declarado");
            return ERROR;
        }
        if (symbol instanceof VariableSymbol variable) {
            variable.setUsed(true);
            if (!variable.isInitialized()) {
                reporter.error(ctx, GENERAL, "'" + name + "' se usa antes de asignarle un valor");
            }
        }
        noteCapture(name);
        return symbol.getType() == null ? ERROR : symbol.getType();
    }

    // ------------------------------------------------------------------
    // Closures
    // ------------------------------------------------------------------

    /**
     * Si el nombre vive en un ambito cuya funcion duena no es la funcion actual,
     * la funcion actual lo esta capturando. Los globales no cuentan: usarlos no
     * es capturar.
     */
    private void noteCapture(String name) {
        if (functions.isEmpty()) {
            return;
        }
        Scope currentFunction = scopes.current().enclosingFunction();
        if (currentFunction == null) {
            return;
        }
        Scope where = scopes.current().scopeOf(name);
        if (where == null) {
            return;
        }
        Scope ownerFunction = where.enclosingFunction();
        if (ownerFunction == null || ownerFunction == currentFunction) {
            return;
        }
        functions.peek().getCaptures().add(name);
        if (where.resolveLocal(name) instanceof VariableSymbol variable) {
            variable.setCaptured(true);
        }
    }

    // ------------------------------------------------------------------
    // Tipos y utilidades
    // ------------------------------------------------------------------

    private Type resolveType(CompiscriptParser.TypeContext ctx) {
        Type type = resolveBaseType(ctx.baseType());
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof TerminalNode terminal
                    && "[".equals(terminal.getText())) {
                type = new ArrayType(type);
            }
        }
        return type;
    }

    private Type resolveBaseType(CompiscriptParser.BaseTypeContext ctx) {
        String name = ctx.getText();
        switch (name) {
            case "integer":
                return PrimitiveType.INTEGER;
            case "string":
                return PrimitiveType.STRING;
            case "boolean":
                return PrimitiveType.BOOLEAN;
            case "float":
                return PrimitiveType.FLOAT;
            default:
                if (scopes.resolve(name) instanceof ClassSymbol classSymbol) {
                    return classSymbol.getClassType();
                }
                reporter.error(ctx, AMBITO, "el tipo '" + name + "' no esta declarado");
                return ERROR;
        }
    }

    private static boolean isNumeric(Type type) {
        return type instanceof PrimitiveType primitive && primitive.isNumeric();
    }

    // Los operadores de las reglas binarias no llevan etiqueta, asi que no hay
    // accesor: los hijos alternan regla, operador, regla.
    private static Token operatorAt(ParserRuleContext ctx, int index) {
        return ((TerminalNode) ctx.getChild(index)).getSymbol();
    }

    /** Baja por la cadena de precedencia, que sin operadores es un solo hijo. */
    private static ParseTree unwrapSingleChild(ParseTree node) {
        while (node.getChildCount() == 1 && node.getChild(0) instanceof ParserRuleContext) {
            node = node.getChild(0);
        }
        return node;
    }

    private static int literalLength(CompiscriptParser.ExpressionContext ctx) {
        // unwrapSingleChild atraviesa literalExpr y llega hasta el arrayLiteral.
        if (unwrapSingleChild(ctx) instanceof CompiscriptParser.ArrayLiteralContext array) {
            return array.expression().size();
        }
        return -1;
    }

    private static int literalInteger(CompiscriptParser.ExpressionContext ctx) {
        ParseTree node = unwrapSingleChild(ctx);
        if (node instanceof CompiscriptParser.LiteralExprContext literal
                && literal.Literal() != null) {
            String text = literal.Literal().getText();
            if (!text.isEmpty() && text.chars().allMatch(Character::isDigit)) {
                return Integer.parseInt(text);
            }
        }
        return -1;
    }

    private void reportUnused(Scope scope) {
        for (Symbol symbol : scope.getSymbols()) {
            if (symbol instanceof VariableSymbol variable && !variable.isUsed()) {
                reporter.warning(variable.getLine(), variable.getColumn(), GENERAL,
                        "'" + variable.getName() + "' esta declarada pero nunca se usa");
            }
        }
    }
}
