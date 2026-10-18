package com.uvg.compiscript.codegen;

import com.uvg.compiscript.parser.CompiscriptBaseVisitor;
import com.uvg.compiscript.parser.CompiscriptParser;
import com.uvg.compiscript.semantic.Annotations;
import com.uvg.compiscript.symbols.ClassSymbol;
import com.uvg.compiscript.symbols.FunctionSymbol;
import com.uvg.compiscript.symbols.MemoryLayout;
import com.uvg.compiscript.symbols.Scope;
import com.uvg.compiscript.symbols.Symbol;
import com.uvg.compiscript.types.ArrayType;
import com.uvg.compiscript.types.FunctionType;
import com.uvg.compiscript.types.PrimitiveType;
import com.uvg.compiscript.types.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Traduce el arbol ya verificado a codigo de tres direcciones.
 *
 * <p>Cada {@code visitXxx} de expresion devuelve el operando donde quedo su
 * valor: una constante, una direccion ({@code G[n]}, {@code fp[n]}) o un
 * temporal. Quien consume el operando lo libera, y asi el temporal vuelve al
 * pool en cuanto deja de hacer falta.
 */
public final class CodeGenerator extends CompiscriptBaseVisitor<String> {

    private final Annotations notes;
    private final Scope global;
    private final List<FunctionCode> functions = new ArrayList<>();
    private final List<ClassSymbol> classes = new ArrayList<>();
    private final Deque<FunctionCode> stack = new ArrayDeque<>();
    private final Deque<String> breakTargets = new ArrayDeque<>();
    private final Deque<String> continueTargets = new ArrayDeque<>();
    private int labels;
    private String comment;

    public CodeGenerator(Annotations notes, Scope global) {
        this.notes = notes;
        this.global = global;
    }

    public TacProgram generate(ParseTree tree) {
        visit(tree);
        return new TacProgram(functions, classes, global);
    }

    // ------------------------------------------------------------------
    // Funciones y emision
    // ------------------------------------------------------------------

    @Override
    public String visitProgram(CompiscriptParser.ProgramContext ctx) {
        FunctionCode main = open("main", null);
        ctx.statement().forEach(this::visit);
        close(main);
        return null;
    }

    @Override
    public String visitFunctionDeclaration(CompiscriptParser.FunctionDeclarationContext ctx) {
        FunctionSymbol function = (FunctionSymbol) notes.symbol(ctx);
        FunctionCode code = open(function.getLabel(), function);
        ctx.block().statement().forEach(this::visit);
        close(code);
        return null;
    }

    private FunctionCode open(String label, FunctionSymbol symbol) {
        FunctionCode code = new FunctionCode(label, symbol);
        functions.add(code);
        stack.push(code);
        return code;
    }

    /** Los temporales ocupan el final del marco, despues de parametros y locales. */
    private void close(FunctionCode code) {
        stack.pop();
        int temps = code.temps.count();
        int locals = code.symbol == null ? MemoryLayout.FRAME_HEADER : code.symbol.getLocalsEnd();
        code.frameSize = locals + temps * MemoryLayout.WORD;
        if (code.symbol != null) {
            code.symbol.setTempCount(temps);
            code.symbol.setFrameSize(code.frameSize);
        }
    }

    private void emit(String text) {
        stack.peek().code.add(new Instruction(text, comment));
        comment = null;
    }

    private void mark(String label) {
        stack.peek().code.add(new Instruction(label + ":", null));
    }

    private String newLabel() {
        return "L" + (++labels);
    }

    private String temp() {
        return stack.peek().temps.take();
    }

    private void free(String operand) {
        stack.peek().temps.release(operand);
    }

    private String toTemp(String operand) {
        if (TempPool.isTemp(operand)) {
            return operand;
        }
        String temp = temp();
        emit(temp + " = " + operand);
        return temp;
    }

    // ------------------------------------------------------------------
    // Sentencias
    // ------------------------------------------------------------------

    @Override
    public String visitStatement(CompiscriptParser.StatementContext ctx) {
        if (ctx.variableDeclaration() != null || ctx.constantDeclaration() != null
                || ctx.assignment() != null || ctx.expressionStatement() != null
                || ctx.printStatement() != null || ctx.returnStatement() != null
                || ctx.breakStatement() != null || ctx.continueStatement() != null) {
            comment = source(ctx);
        }
        visit(ctx.getChild(0));
        comment = null;
        return null;
    }

    @Override
    public String visitBlock(CompiscriptParser.BlockContext ctx) {
        ctx.statement().forEach(this::visit);
        return null;
    }

    @Override
    public String visitVariableDeclaration(CompiscriptParser.VariableDeclarationContext ctx) {
        if (ctx.initializer() != null) {
            assign(notes.symbol(ctx), ctx.initializer().expression());
        }
        return null;
    }

    @Override
    public String visitConstantDeclaration(CompiscriptParser.ConstantDeclarationContext ctx) {
        assign(notes.symbol(ctx), ctx.expression());
        return null;
    }

    @Override
    public String visitAssignment(CompiscriptParser.AssignmentContext ctx) {
        if (ctx.expression().size() == 1) {
            assign(notes.symbol(ctx), ctx.expression(0));
            return null;
        }
        String object = visit(ctx.expression(0));
        Symbol field = notes.symbol(ctx);
        String value = value(ctx.expression(1), field.getType());
        storeField(object, field, value);
        free(value);
        return null;
    }

    @Override
    public String visitPrintStatement(CompiscriptParser.PrintStatementContext ctx) {
        String value = visit(ctx.expression());
        emit("print " + value);
        free(value);
        return null;
    }

    @Override
    public String visitExpressionStatement(CompiscriptParser.ExpressionStatementContext ctx) {
        free(visit(ctx.expression()));
        return null;
    }

    @Override
    public String visitReturnStatement(CompiscriptParser.ReturnStatementContext ctx) {
        if (ctx.expression() == null) {
            emit("return");
            return null;
        }
        String value = value(ctx.expression(), stack.peek().symbol.getReturnType());
        emit("return " + value);
        free(value);
        return null;
    }

    @Override
    public String visitIfStatement(CompiscriptParser.IfStatementContext ctx) {
        boolean hasElse = ctx.block().size() == 2;
        String otherwise = hasElse ? newLabel() : null;
        String end = newLabel();
        jumpIfFalse(ctx.expression(), hasElse ? otherwise : end);
        visit(ctx.block(0));
        if (hasElse) {
            emit("goto " + end);
            mark(otherwise);
            visit(ctx.block(1));
        }
        mark(end);
        return null;
    }

    @Override
    public String visitWhileStatement(CompiscriptParser.WhileStatementContext ctx) {
        String test = newLabel();
        String end = newLabel();
        mark(test);
        jumpIfFalse(ctx.expression(), end);
        loopBody(ctx.block(), end, test);
        emit("goto " + test);
        mark(end);
        return null;
    }

    @Override
    public String visitDoWhileStatement(CompiscriptParser.DoWhileStatementContext ctx) {
        String start = newLabel();
        String test = newLabel();
        String end = newLabel();
        mark(start);
        loopBody(ctx.block(), end, test);
        mark(test);
        jumpIfTrue(ctx.expression(), start);
        mark(end);
        return null;
    }

    @Override
    public String visitForStatement(CompiscriptParser.ForStatementContext ctx) {
        if (ctx.variableDeclaration() != null) {
            comment = source(ctx.variableDeclaration());
            visit(ctx.variableDeclaration());
        } else if (ctx.assignment() != null) {
            comment = source(ctx.assignment());
            visit(ctx.assignment());
        }
        String test = newLabel();
        String update = newLabel();
        String end = newLabel();
        mark(test);
        CompiscriptParser.ExpressionContext condition = forExpression(ctx, true);
        if (condition != null) {
            jumpIfFalse(condition, end);
        }
        loopBody(ctx.block(), end, update);
        mark(update);
        CompiscriptParser.ExpressionContext step = forExpression(ctx, false);
        if (step != null) {
            comment = source(step);
            free(visit(step));
        }
        emit("goto " + test);
        mark(end);
        return null;
    }

    /** Mismo criterio que el semantico: la posicion respecto al {@code ;} separador. */
    private static CompiscriptParser.ExpressionContext forExpression(
            CompiscriptParser.ForStatementContext ctx, boolean wantCondition) {
        int separator = (ctx.variableDeclaration() != null || ctx.assignment() != null) ? 0 : 1;
        int seen = 0;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode terminal && ";".equals(terminal.getText())) {
                seen++;
            } else if (child instanceof CompiscriptParser.ExpressionContext expression
                    && (seen <= separator) == wantCondition) {
                return expression;
            }
        }
        return null;
    }

    private void loopBody(ParseTree body, String exit, String next) {
        breakTargets.push(exit);
        continueTargets.push(next);
        visit(body);
        continueTargets.pop();
        breakTargets.pop();
    }

    /**
     * El selector se evalua una vez; cada {@code case} compara contra el y los
     * cuerpos van uno tras otro, asi que sin {@code break} se cae al siguiente.
     */
    @Override
    public String visitSwitchStatement(CompiscriptParser.SwitchStatementContext ctx) {
        String selector = visit(ctx.expression());
        Type selectorType = notes.type(ctx.expression());
        boolean floatCase = ctx.switchCase().stream()
                .anyMatch(c -> notes.type(c.expression()) == PrimitiveType.FLOAT);
        if (floatCase) {
            selector = convert(selector, selectorType, PrimitiveType.FLOAT);
            selectorType = PrimitiveType.FLOAT;
        }

        List<String> targets = new ArrayList<>();
        for (CompiscriptParser.SwitchCaseContext switchCase : ctx.switchCase()) {
            String target = newLabel();
            targets.add(target);
            String label = value(switchCase.expression(), selectorType);
            emit("if " + selector + " == " + label + " goto " + target);
            free(label);
        }
        free(selector);
        String fallback = ctx.defaultCase() != null ? newLabel() : null;
        String end = newLabel();
        emit("goto " + (fallback != null ? fallback : end));

        breakTargets.push(end);
        for (int i = 0; i < targets.size(); i++) {
            mark(targets.get(i));
            ctx.switchCase(i).statement().forEach(this::visit);
        }
        if (fallback != null) {
            mark(fallback);
            ctx.defaultCase().statement().forEach(this::visit);
        }
        breakTargets.pop();
        mark(end);
        return null;
    }

    @Override
    public String visitBreakStatement(CompiscriptParser.BreakStatementContext ctx) {
        emit("goto " + breakTargets.peek());
        return null;
    }

    @Override
    public String visitContinueStatement(CompiscriptParser.ContinueStatementContext ctx) {
        emit("goto " + continueTargets.peek());
        return null;
    }

    @Override
    public String visitForeachStatement(CompiscriptParser.ForeachStatementContext ctx) {
        throw unsupported("foreach");
    }

    @Override
    public String visitTryCatchStatement(CompiscriptParser.TryCatchStatementContext ctx) {
        throw unsupported("try/catch");
    }

    @Override
    public String visitClassDeclaration(CompiscriptParser.ClassDeclarationContext ctx) {
        throw unsupported("clases");
    }

    // ------------------------------------------------------------------
    // Variables: donde vive cada simbolo
    // ------------------------------------------------------------------

    private void assign(Symbol target, ParseTree expression) {
        String value = value(expression, target.getType());
        store(target, value);
        free(value);
    }

    private void store(Symbol symbol, String value) {
        if (isField(symbol)) {
            storeField(thisPointer(), symbol, value);
        } else if (isNonLocal(symbol)) {
            String frame = frameOf(symbol);
            emit(frame + "[" + symbol.getOffset() + "] = " + value);
            free(frame);
        } else {
            emit(symbol.getAddress() + " = " + value);
        }
    }

    private String load(Symbol symbol) {
        if (isField(symbol)) {
            return readField(thisPointer(), symbol);
        }
        if (isNonLocal(symbol)) {
            String frame = frameOf(symbol);
            free(frame);
            String temp = temp();
            emit(temp + " = " + frame + "[" + symbol.getOffset() + "]");
            return temp;
        }
        return symbol.getAddress();
    }

    private static boolean isField(Symbol symbol) {
        return symbol.getScope().getKind() == Scope.Kind.CLASS;
    }

    /** Vive en el marco de otra funcion: la que encierra a la actual. */
    private boolean isNonLocal(Symbol symbol) {
        Scope owner = symbol.getScope().enclosingFunction();
        return owner != null && owner != currentBody();
    }

    private Scope currentBody() {
        FunctionSymbol function = stack.peek().symbol;
        return function == null ? null : function.getBodyScope();
    }

    private int depth() {
        Scope body = currentBody();
        return body == null ? 0 : body.getFunctionDepth();
    }

    private String frameOf(Symbol symbol) {
        return access(depth() - symbol.getScope().getFunctionDepth());
    }

    /** Sube {@code levels} enlaces de acceso desde el marco actual. */
    private String access(int levels) {
        String temp = temp();
        emit(temp + " = access " + levels);
        return temp;
    }

    private String thisPointer() {
        throw unsupported("this");
    }

    private void storeField(String object, Symbol field, String value) {
        throw unsupported("atributos");
    }

    private String readField(String object, Symbol field) {
        throw unsupported("atributos");
    }

    // ------------------------------------------------------------------
    // Expresiones
    // ------------------------------------------------------------------

    /** Evalua y convierte al tipo del destino: integer promueve a float. */
    private String value(ParseTree expression, Type target) {
        return convert(visit(expression), notes.type(expression), target);
    }

    private String convert(String operand, Type from, Type to) {
        if (to != PrimitiveType.FLOAT || from != PrimitiveType.INTEGER) {
            return operand;
        }
        if (operand.matches("-?\\d+")) {
            return operand + ".0";
        }
        free(operand);
        String temp = temp();
        emit(temp + " = (float) " + operand);
        return temp;
    }

    private String[] numericPair(String left, Type leftType, String right, Type rightType) {
        if (leftType == PrimitiveType.FLOAT && rightType == PrimitiveType.INTEGER) {
            right = convert(right, rightType, PrimitiveType.FLOAT);
        } else if (leftType == PrimitiveType.INTEGER && rightType == PrimitiveType.FLOAT) {
            left = convert(left, leftType, PrimitiveType.FLOAT);
        }
        return new String[]{left, right};
    }

    @Override
    public String visitExpression(CompiscriptParser.ExpressionContext ctx) {
        return visit(ctx.assignmentExpr());
    }

    @Override
    public String visitExprNoAssign(CompiscriptParser.ExprNoAssignContext ctx) {
        return visit(ctx.conditionalExpr());
    }

    @Override
    public String visitAssignExpr(CompiscriptParser.AssignExprContext ctx) {
        CompiscriptParser.LeftHandSideContext lhs = ctx.lhs;
        int count = lhs.suffixOp().size();
        if (count == 0) {
            Symbol target = notes.symbol(ctx);
            String value = value(ctx.assignmentExpr(), target.getType());
            store(target, value);
            return value;
        }
        String base = fold(lhs, count - 1);
        if (lhs.suffixOp(count - 1) instanceof CompiscriptParser.PropertyAccessExprContext) {
            Symbol field = notes.symbol(ctx);
            String value = value(ctx.assignmentExpr(), field.getType());
            storeField(base, field, value);
            return value;
        }
        CompiscriptParser.IndexExprContext index =
                (CompiscriptParser.IndexExprContext) lhs.suffixOp(count - 1);
        return storeElement(base, index, (ArrayType) typeBefore(lhs, count - 1),
                ctx.assignmentExpr());
    }

    @Override
    public String visitPropertyAssignExpr(CompiscriptParser.PropertyAssignExprContext ctx) {
        String base = fold(ctx.lhs, ctx.lhs.suffixOp().size());
        Symbol field = notes.symbol(ctx);
        String value = value(ctx.assignmentExpr(), field.getType());
        storeField(base, field, value);
        return value;
    }

    @Override
    public String visitTernaryExpr(CompiscriptParser.TernaryExprContext ctx) {
        if (ctx.expression().isEmpty()) {
            return visit(ctx.logicalOrExpr());
        }
        Type type = notes.type(ctx);
        String otherwise = newLabel();
        String end = newLabel();
        jumpIfFalse(ctx.logicalOrExpr(), otherwise);
        String result = temp();
        String first = value(ctx.expression(0), type);
        emit(result + " = " + first);
        free(first);
        emit("goto " + end);
        mark(otherwise);
        String second = value(ctx.expression(1), type);
        emit(result + " = " + second);
        free(second);
        mark(end);
        return result;
    }

    @Override
    public String visitLogicalOrExpr(CompiscriptParser.LogicalOrExprContext ctx) {
        return ctx.logicalAndExpr().size() == 1 ? visit(ctx.logicalAndExpr(0)) : booleanValue(ctx);
    }

    @Override
    public String visitLogicalAndExpr(CompiscriptParser.LogicalAndExprContext ctx) {
        return ctx.equalityExpr().size() == 1 ? visit(ctx.equalityExpr(0)) : booleanValue(ctx);
    }

    /** {@code &&} y {@code ||} usados como valor: los saltos dejan true o false en un temporal. */
    private String booleanValue(ParserRuleContext condition) {
        String no = newLabel();
        String end = newLabel();
        jumpIfFalse(condition, no);
        String result = temp();
        emit(result + " = true");
        emit("goto " + end);
        mark(no);
        emit(result + " = false");
        mark(end);
        return result;
    }

    @Override
    public String visitEqualityExpr(CompiscriptParser.EqualityExprContext ctx) {
        return comparisons(ctx, ctx.relationalExpr());
    }

    @Override
    public String visitRelationalExpr(CompiscriptParser.RelationalExprContext ctx) {
        return comparisons(ctx, ctx.additiveExpr());
    }

    private String comparisons(ParserRuleContext ctx, List<? extends ParserRuleContext> parts) {
        if (parts.size() == 1) {
            return visit(parts.get(0));
        }
        String left = visit(parts.get(0));
        Type leftType = notes.type(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            String[] pair = numericPair(left, leftType,
                    visit(parts.get(i)), notes.type(parts.get(i)));
            left = binary(pair[0], operator(ctx, i), pair[1]);
            leftType = PrimitiveType.BOOLEAN;
        }
        return left;
    }

    @Override
    public String visitAdditiveExpr(CompiscriptParser.AdditiveExprContext ctx) {
        return arithmetic(ctx, ctx.multiplicativeExpr());
    }

    @Override
    public String visitMultiplicativeExpr(CompiscriptParser.MultiplicativeExprContext ctx) {
        return arithmetic(ctx, ctx.unaryExpr());
    }

    /** Asociatividad izquierda: cada resultado parcial es el operando izquierdo del siguiente. */
    private String arithmetic(ParserRuleContext ctx, List<? extends ParserRuleContext> parts) {
        if (parts.size() == 1) {
            return visit(parts.get(0));
        }
        String left = visit(parts.get(0));
        Type leftType = notes.type(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            String right = visit(parts.get(i));
            Type rightType = notes.type(parts.get(i));
            if (leftType == PrimitiveType.STRING) {
                free(left);
                free(right);
                String temp = temp();
                emit(temp + " = concat " + left + ", " + right);
                left = temp;
                continue;
            }
            String[] pair = numericPair(left, leftType, right, rightType);
            left = binary(pair[0], operator(ctx, i), pair[1]);
            leftType = leftType == PrimitiveType.FLOAT || rightType == PrimitiveType.FLOAT
                    ? PrimitiveType.FLOAT : PrimitiveType.INTEGER;
        }
        return left;
    }

    private String binary(String left, String operator, String right) {
        free(left);
        free(right);
        String temp = temp();
        emit(temp + " = " + left + " " + operator + " " + right);
        return temp;
    }

    @Override
    public String visitUnaryExpr(CompiscriptParser.UnaryExprContext ctx) {
        if (ctx.primaryExpr() != null) {
            return visit(ctx.primaryExpr());
        }
        String operator = ctx.getChild(0).getText();
        if ("-".equals(operator) && strip(ctx.unaryExpr())
                instanceof CompiscriptParser.LiteralExprContext literal && literal.Literal() != null) {
            return "-" + literal.getText();
        }
        String operand = visit(ctx.unaryExpr());
        free(operand);
        String temp = temp();
        emit(temp + " = " + operator + " " + operand);
        return temp;
    }

    @Override
    public String visitPrimaryExpr(CompiscriptParser.PrimaryExprContext ctx) {
        if (ctx.literalExpr() != null) {
            return visit(ctx.literalExpr());
        }
        if (ctx.leftHandSide() != null) {
            return visit(ctx.leftHandSide());
        }
        return visit(ctx.expression());
    }

    @Override
    public String visitLiteralExpr(CompiscriptParser.LiteralExprContext ctx) {
        return ctx.arrayLiteral() != null ? visit(ctx.arrayLiteral()) : ctx.getText();
    }

    @Override
    public String visitArrayLiteral(CompiscriptParser.ArrayLiteralContext ctx) {
        throw unsupported("listas");
    }

    @Override
    public String visitIdentifierExpr(CompiscriptParser.IdentifierExprContext ctx) {
        return load(notes.symbol(ctx));
    }

    @Override
    public String visitThisExpr(CompiscriptParser.ThisExprContext ctx) {
        return thisPointer();
    }

    @Override
    public String visitNewExpr(CompiscriptParser.NewExprContext ctx) {
        throw unsupported("new");
    }

    // ------------------------------------------------------------------
    // leftHandSide: primaryAtom (suffixOp)*
    // ------------------------------------------------------------------

    @Override
    public String visitLeftHandSide(CompiscriptParser.LeftHandSideContext ctx) {
        return fold(ctx, ctx.suffixOp().size());
    }

    /** Aplica los primeros {@code count} sufijos, igual que el semantico. */
    private String fold(CompiscriptParser.LeftHandSideContext ctx, int count) {
        int next = 0;
        String current;
        if (ctx.primaryAtom() instanceof CompiscriptParser.IdentifierExprContext identifier
                && notes.symbol(identifier) instanceof FunctionSymbol function) {
            if (count > 0 && ctx.suffixOp(0) instanceof CompiscriptParser.CallExprContext call) {
                current = callFunction(function, call);
                next = 1;
            } else {
                current = functionValue(function);
            }
        } else {
            current = visit(ctx.primaryAtom());
        }

        for (int i = next; i < count; i++) {
            CompiscriptParser.SuffixOpContext suffix = ctx.suffixOp(i);
            if (suffix instanceof CompiscriptParser.PropertyAccessExprContext property) {
                if (notes.symbol(property) instanceof FunctionSymbol method) {
                    i++;
                    current = callMethod(current, method,
                            (CompiscriptParser.CallExprContext) ctx.suffixOp(i));
                } else {
                    current = readProperty(current, property, typeBefore(ctx, i));
                }
            } else if (suffix instanceof CompiscriptParser.IndexExprContext index) {
                current = readElement(current, index, (ArrayType) typeBefore(ctx, i));
            } else {
                current = callValue(current, (CompiscriptParser.CallExprContext) suffix,
                        (FunctionType) typeBefore(ctx, i));
            }
        }
        return current;
    }

    private Type typeBefore(CompiscriptParser.LeftHandSideContext ctx, int position) {
        return position == 0 ? notes.type(ctx.primaryAtom()) : notes.type(ctx.suffixOp(position - 1));
    }

    private String readProperty(String object, CompiscriptParser.PropertyAccessExprContext property,
                                Type objectType) {
        throw unsupported("propiedades");
    }

    private String readElement(String array, CompiscriptParser.IndexExprContext index,
                               ArrayType type) {
        throw unsupported("listas");
    }

    private String storeElement(String array, CompiscriptParser.IndexExprContext index,
                                ArrayType type, ParseTree value) {
        throw unsupported("listas");
    }

    // ------------------------------------------------------------------
    // Llamadas
    // ------------------------------------------------------------------

    private String callFunction(FunctionSymbol function, CompiscriptParser.CallExprContext call) {
        if (function.isMethod()) {
            return callMethod(thisPointer(), function, call);
        }
        List<String> arguments = arguments(call, (FunctionType) function.getType());
        String link = function.getScope().enclosingFunction() == null ? null : staticLink(function);
        return call(function.getLabel(), arguments, link, function.getReturnType());
    }

    /**
     * El enlace de acceso de una funcion anidada es el marco de la funcion que
     * la declara: el actual si la declaro esta, o uno de mas arriba.
     */
    private String staticLink(FunctionSymbol function) {
        int levels = depth() - function.getScope().enclosingFunction().getFunctionDepth();
        return levels == 0 ? "fp" : access(levels);
    }

    private List<String> arguments(CompiscriptParser.CallExprContext call, FunctionType signature) {
        List<String> values = new ArrayList<>();
        if (call.arguments() != null) {
            List<CompiscriptParser.ExpressionContext> expressions = call.arguments().expression();
            for (int i = 0; i < expressions.size(); i++) {
                values.add(value(expressions.get(i), signature.getParameterTypes().get(i)));
            }
        }
        return values;
    }

    /** Los argumentos ya estan evaluados: los {@code param} van seguidos, sin codigo en medio. */
    private String call(String target, List<String> arguments, String link, Type returnType) {
        arguments.forEach(argument -> emit("param " + argument));
        arguments.forEach(this::free);
        if (link != null) {
            emit("link " + link);
            free(link);
        }
        String call = "call " + target + ", " + arguments.size();
        if (returnType == PrimitiveType.VOID) {
            emit(call);
            free(target);
            return null;
        }
        String result = temp();
        emit(result + " = " + call);
        free(target);
        return result;
    }

    private String callMethod(String object, FunctionSymbol method,
                              CompiscriptParser.CallExprContext call) {
        throw unsupported("metodos");
    }

    private String callValue(String function, CompiscriptParser.CallExprContext call,
                             FunctionType signature) {
        throw unsupported("llamadas indirectas");
    }

    private String functionValue(FunctionSymbol function) {
        throw unsupported("funciones como valor");
    }

    // ------------------------------------------------------------------
    // Condiciones como codigo de saltos
    // ------------------------------------------------------------------

    private void jumpIfFalse(ParseTree condition, String target) {
        jump(condition, target, false);
    }

    private void jumpIfTrue(ParseTree condition, String target) {
        jump(condition, target, true);
    }

    /** Salta a {@code target} si la condicion vale {@code when}; si no, sigue de largo. */
    private void jump(ParseTree condition, String target, boolean when) {
        ParseTree node = strip(condition);
        if (node instanceof CompiscriptParser.LogicalOrExprContext or
                && or.logicalAndExpr().size() > 1) {
            shortCircuit(or.logicalAndExpr(), target, when, true);
            return;
        }
        if (node instanceof CompiscriptParser.LogicalAndExprContext and
                && and.equalityExpr().size() > 1) {
            shortCircuit(and.equalityExpr(), target, when, false);
            return;
        }
        if (node instanceof CompiscriptParser.UnaryExprContext unary && unary.primaryExpr() == null
                && "!".equals(unary.getChild(0).getText())) {
            jump(unary.unaryExpr(), target, !when);
            return;
        }
        if (node instanceof CompiscriptParser.LiteralExprContext literal
                && ("true".equals(literal.getText()) || "false".equals(literal.getText()))) {
            if (Boolean.parseBoolean(literal.getText()) == when) {
                emit("goto " + target);
            }
            return;
        }
        String test = comparison(node);
        if (test == null) {
            test = visit(node);
            free(test);
        }
        emit((when ? "if " : "ifFalse ") + test + " goto " + target);
    }

    /**
     * En un {@code ||} basta un operando verdadero y en un {@code &&} uno falso
     * para decidir. Cuando se busca el otro resultado, los operandos que deciden
     * saltan a una etiqueta que deja pasar al siguiente codigo.
     */
    private void shortCircuit(List<? extends ParserRuleContext> parts, String target, boolean when,
                              boolean or) {
        if (or == when) {
            parts.forEach(part -> jump(part, target, when));
            return;
        }
        String skip = newLabel();
        for (int i = 0; i < parts.size() - 1; i++) {
            jump(parts.get(i), skip, !when);
        }
        jump(parts.get(parts.size() - 1), target, when);
        mark(skip);
    }

    /** {@code a < b} cabe entero en un salto condicional: {@code if a < b goto L}. */
    private String comparison(ParseTree node) {
        List<? extends ParserRuleContext> parts;
        if (node instanceof CompiscriptParser.EqualityExprContext equality
                && equality.relationalExpr().size() == 2) {
            parts = equality.relationalExpr();
        } else if (node instanceof CompiscriptParser.RelationalExprContext relational
                && relational.additiveExpr().size() == 2) {
            parts = relational.additiveExpr();
        } else {
            return null;
        }
        String left = visit(parts.get(0));
        String right = visit(parts.get(1));
        String[] pair = numericPair(left, notes.type(parts.get(0)), right, notes.type(parts.get(1)));
        free(pair[0]);
        free(pair[1]);
        return pair[0] + " " + operator((ParserRuleContext) node, 1) + " " + pair[1];
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Baja por la cadena de precedencia y los parentesis hasta el nodo que hace algo. */
    private static ParseTree strip(ParseTree node) {
        while (true) {
            if (node instanceof CompiscriptParser.PrimaryExprContext primary
                    && primary.expression() != null) {
                node = primary.expression();
            } else if (node.getChildCount() == 1
                    && node.getChild(0) instanceof ParserRuleContext child) {
                node = child;
            } else {
                return node;
            }
        }
    }

    // Los operadores de las reglas binarias no tienen etiqueta: alternan con los operandos.
    private static String operator(ParserRuleContext ctx, int index) {
        return ctx.getChild(2 * index - 1).getText();
    }

    private static String source(ParserRuleContext ctx) {
        return ctx.start.getInputStream()
                .getText(Interval.of(ctx.start.getStartIndex(), ctx.stop.getStopIndex()))
                .replaceAll("\\s+", " ");
    }

    private static IllegalStateException unsupported(String what) {
        return new IllegalStateException("generacion de codigo aun no soportada: " + what);
    }
}
