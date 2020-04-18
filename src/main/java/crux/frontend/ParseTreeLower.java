package crux.frontend;

import crux.frontend.ast.*;
import crux.frontend.ast.OpExpr.Operation;
import crux.frontend.pt.CruxBaseVisitor;
import crux.frontend.pt.CruxParser;
import crux.frontend.types.*;
import org.antlr.v4.runtime.ParserRuleContext;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In this class, you're going to implement functionality that transform a input ParseTree
 * into an AST tree.
 *
 * The lowering process would start with {@link #lower(CruxParser.ProgramContext)}. Which take top-level
 * parse tree as input and process its children, function definitions and array declarations for example,
 * using other utilities functions or classes, like {@link #lower(CruxParser.StatementListContext)} or {@link DeclarationVisitor},
 * recursively.
 * */
public final class ParseTreeLower {
    private final DeclarationVisitor declarationVisitor = new DeclarationVisitor();
    private final StatementVisitor statementVisitor = new StatementVisitor();
    private final ExpressionVisitor expressionVisitor = new ExpressionVisitor(true);
    private final ExpressionVisitor locationVisitor = new ExpressionVisitor(false);

    private final SymbolTable symTab;

    public ParseTreeLower(PrintStream err) {
        symTab = new SymbolTable(err);
    }

    private static Position makePosition(ParserRuleContext ctx) {
        var start = ctx.start;
        return new Position(start.getLine(), start.getCharPositionInLine());
    }

    /**
     * Should returns true if we have encountered an error.
     */
    public boolean hasEncounteredError() {
        return symTab.hasEncounteredError();
    }

    /**
     * Lower top-level parse tree to AST
     * @return a {@link DeclarationList} object representing the top-level AST.
     * */
    public DeclarationList lower(CruxParser.ProgramContext program) {
        List<Declaration> result = new ArrayList<>();
        for (CruxParser.DeclarationContext declaration : program.declarationList().declaration()){
            if (declaration.functionDefinition() != null) {
                result.add(declaration.functionDefinition().accept(declarationVisitor));
            } else if (declaration.variableDeclaration() != null) {
               result.add(declaration.variableDeclaration().accept(declarationVisitor));
            } else {  // can only be one of these three cases.
                result.add(declaration.arrayDeclaration().accept(declarationVisitor));
            }
        }
        if (hasEncounteredError()) // handle error here
            return null;
        else
            return new DeclarationList(makePosition(program), result);
    }

    /**
     * Lower statement list by lower individual statement into AST.
     * @return a {@link StatementList} AST object.
     * */
    private StatementList lower(CruxParser.StatementListContext statementList) {
        List<Statement> result = new ArrayList<>();
        for (CruxParser.StatementContext statement : statementList.statement()) {
            if (statement.variableDeclaration() != null) {
                result.add(statement.variableDeclaration().accept(statementVisitor));
            } else if (statement.callStatement() != null) {  // the statement is a callStatement
                result.add(statement.callStatement().accept(statementVisitor));
            } else if (statement.assignmentStatement() != null) {
                result.add(statement.assignmentStatement().accept(statementVisitor));
            } else if (statement.ifStatement() != null) {
                result.add(statement.ifStatement().accept(statementVisitor));
            } else if (statement.whileStatement() != null) {
                result.add(statement.whileStatement().accept(statementVisitor));
            } else if (statement.returnStatement() != null) {
                result.add(statement.returnStatement().accept(statementVisitor));
            } else {
                return null;  // idk what to do here...
//                throw new Exception("reached else statement in lower(CruxParser.StatementListContext)");
            }
        }
        return new StatementList(makePosition(statementList), result);
    }

    /**
     * Similar to {@link #lower(CruxParser.StatementListContext)}, but handling symbol table
     * as well.
     * @return a {@link StatementList} AST object.
     * */
    private StatementList lower(CruxParser.StatementBlockContext statementBlock) {
        symTab.enter();
        StatementList result = lower(statementBlock.statementList());
        symTab.exit();
        return result;
    }


    /**
     * A parse tree visitor to create AST nodes derived from {@link Declaration}
     * */
    private final class DeclarationVisitor extends CruxBaseVisitor<Declaration> {
        /**
         * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}
         * @return an AST {@link VariableDeclaration}
         * */
        @Override
        public VariableDeclaration visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {
            String name = ctx.Identifier().getSymbol().getText();
            Position pos = makePosition(ctx);
            Symbol symbol = symTab.add(pos, name);
            return new VariableDeclaration(pos, symbol);
        }

        /**
         * Visit a parse tree array declaration and create an AST {@link ArrayDeclaration}
         * @return an AST {@link ArrayDeclaration}
         * */
        @Override
        public ArrayDeclaration visitArrayDeclaration(CruxParser.ArrayDeclarationContext ctx) {
            String name = ctx.Identifier().getSymbol().getText();
            Position pos = makePosition(ctx);
            Symbol symbol = symTab.add(pos, name);
            return new ArrayDeclaration(pos, symbol);
        }

        /**
         * Visit a parse tree function definition and create an AST {@link FunctionDefinition}
         * @return an AST {@link FunctionDefinition}
         * */
        @Override
        public FunctionDefinition visitFunctionDefinition(CruxParser.FunctionDefinitionContext ctx) {
            Symbol symb = new Symbol(ctx.Identifier().getSymbol().getText());
//                    , ctx.type().Identifier().getSymbol().getText());
            List<Symbol> params = new ArrayList<>();
            for (CruxParser.ParameterContext param : ctx.parameterList().parameter()){
                params.add(new Symbol(param.Identifier().getSymbol().getText()));
            }
            StatementList statements = lower(ctx.statementBlock());  // lower statementlist
            return new FunctionDefinition(makePosition(ctx), symb, params, statements);
        }
    }

    /**
     * A parse tree visitor to create AST nodes derived from {@link Statement}
     * */
    private final class StatementVisitor extends CruxBaseVisitor<Statement> {
        /**
         * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}.
         * Since {@link VariableDeclaration} is both {@link Declaration} and {@link Statement},
         * we simply delegate this to {@link DeclarationVisitor#visitArrayDeclaration(CruxParser.ArrayDeclarationContext)}
         * which we implement earlier.
         * @return an AST {@link VariableDeclaration}
         * */
        @Override
        public Statement visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {
            return declarationVisitor.visitVariableDeclaration(ctx);
        }


        /**
         * Visit a parse tree assignment statement and create an AST {@link Assignment}
         * @return an AST {@link Assignment}
         * */
        @Override
        public Statement visitAssignmentStatement(CruxParser.AssignmentStatementContext ctx) {
            return new Assignment(makePosition(ctx),
                    locationVisitor.visitDesignator(ctx.designator()),
                    expressionVisitor.visitExpression0(ctx.expression0()));
        }


        /**
         * Visit a parse tree call statement and create an AST {@link Call}.
         * Since {@link Call} is both {@link Expression} and {@link Statement},
         * we simply delegate this to {@link ExpressionVisitor#visitCallExpression(CruxParser.CallExpressionContext)}
         * that we will implement later.
         * @return an AST {@link Call}
         * */
        @Override
        public Statement visitCallStatement(CruxParser.CallStatementContext ctx) {
            return expressionVisitor.visitCallExpression(ctx.callExpression());
        }


        /**
         * Visit a parse tree if-else branch and create an AST {@link IfElseBranch}.
         * The template code shows partial implementations that visit the then block and else block
         * recursively before using those returned AST nodes to construct {@link IfElseBranch} object.
         * @return an AST {@link IfElseBranch}
         * */
        @Override
        public Statement visitIfStatement(CruxParser.IfStatementContext ctx) {
            Expression condition = expressionVisitor.visitExpression0(ctx.expression0());
            StatementList thenBlock = lower(ctx.statementBlock(0));
            StatementList elseBlock = new StatementList(makePosition(ctx),new ArrayList<>());
            if (ctx.Else() != null){
                elseBlock = lower(ctx.statementBlock(1));
            }
            return new IfElseBranch(makePosition(ctx), condition, thenBlock, elseBlock);
        }


        /**
         * Visit a parse tree while loop and create an AST {@link WhileLoop}.
         * You'll going to use a similar techniques as {@link #visitIfStatement(CruxParser.IfStatementContext)}
         * to decompose this construction.
         * @return an AST {@link WhileLoop}
         * */
        @Override
        public Statement visitWhileStatement(CruxParser.WhileStatementContext ctx) {
            Expression condition = expressionVisitor.visitExpression0(ctx.expression0());
            StatementList body = lower(ctx.statementBlock());
            return new WhileLoop(makePosition(ctx), condition, body);
        }

        /**
         * Visit a parse tree return statement and create an AST {@link Return}.
         * Here we show a simple example of how to lower a simple parse tree construction.
         * @return an AST {@link Return}
         * */
        @Override
        public Statement visitReturnStatement(CruxParser.ReturnStatementContext ctx) {
            Expression value = expressionVisitor.visitExpression0(ctx.expression0());
            return new Return(makePosition(ctx), value);
        }


    }

    private final class ExpressionVisitor extends CruxBaseVisitor<Expression> {
        private final boolean dereferenceDesignator;

        private ExpressionVisitor(boolean dereferenceDesignator) {
            this.dereferenceDesignator = dereferenceDesignator;
        }


        @Override
        public Expression visitExpression0(CruxParser.Expression0Context ctx) {
            if( ctx.op0() == null ){    // only one expression 1
                return expressionVisitor.visitExpression1(ctx.expression1(0));
            } else {
                Operation op;
                if (ctx.op0().Greater_equal() != null){
                    op = Operation.GE;
                } else if (ctx.op0().Lesser_equal() != null){
                    op = Operation.LE;
                } else if (ctx.op0().Not_equal() != null){
                    op = Operation.NE;
                } else if (ctx.op0().Equal() != null){
                    op = Operation.EQ;
                } else if (ctx.op0().Greater_than() != null){
                    op = Operation.GT;
                } else if (ctx.op0().Less_than() != null){
                    op = Operation.LT;
                } else {
                    op = null;
                    // error ??
                }
                Expression left = expressionVisitor.visitExpression1(ctx.expression1(0));
                Expression right = expressionVisitor.visitExpression1(ctx.expression1(1));
                return new OpExpr(makePosition(ctx.op0()), op, left, right);
            }
        }


        private Operation getOp1(CruxParser.Op1Context ctx){
            if (ctx.Add() != null) {
                return Operation.ADD;
            } else if (ctx.Sub() != null) {
                return Operation.SUB;
            } else if (ctx.Or() != null) {
                return Operation.LOGIC_OR;
            } else {
                // error
                return null;
            }
        }


        @Override
        public Expression visitExpression1(CruxParser.Expression1Context ctx) {
            if(ctx.op1().size() == 0){  // should be only one expression2
                return expressionVisitor.visitExpression2(ctx.expression2(0));
            } else { // there are multiple op1... we build a opexpr for each
                OpExpr curr = new OpExpr(makePosition(ctx.op1(0)), getOp1(ctx.op1(0)),
                        expressionVisitor.visitExpression2(ctx.expression2(0)),
                        expressionVisitor.visitExpression2(ctx.expression2(1)));
                for (int i = 1; i < ctx.op1().size(); i++){
                    curr = new OpExpr(makePosition(ctx.op1(i)),
                            getOp1(ctx.op1(i)),
                            curr,
                            expressionVisitor.visitExpression2(ctx.expression2(i+1)));
                }
                return curr;
            }
        }


        private Operation getOp2(CruxParser.Op2Context ctx){
            if (ctx.And() != null) {
                return Operation.LOGIC_AND;
            } else if (ctx.Div() != null) {
                return Operation.DIV;
            } else if (ctx.Mul() != null) {
                return Operation.MULT;
            } else {
                // error
                return null;
            }
        }


        @Override
        public Expression visitExpression2(CruxParser.Expression2Context ctx) {
            if(ctx.op2().size() == 0){  // should be only one expression2
                return expressionVisitor.visitExpression3(ctx.expression3(0));
            } else { // there are multiple op1... we build a opexpr for each
                OpExpr curr = new OpExpr(makePosition(ctx.op2(0)), getOp2(ctx.op2(0)),
                        expressionVisitor.visitExpression3(ctx.expression3(0)),
                        expressionVisitor.visitExpression3(ctx.expression3(1)));
                for (int i = 1; i < ctx.op2().size(); i++){
                    curr = new OpExpr(makePosition(ctx.op2(i)),
                            getOp2(ctx.op2(i)),
                            curr,
                            expressionVisitor.visitExpression3(ctx.expression3(i+1)));
                }
                return curr;
            }
        }



        @Override
        public Expression visitExpression3(CruxParser.Expression3Context ctx) {
            if (ctx.Not() != null) {
                return new OpExpr(makePosition(ctx), Operation.LOGIC_NOT,
                        expressionVisitor.visitExpression3(ctx.expression3()), null
                        );
            } else if (ctx.expression0() != null) {
                return expressionVisitor.visitExpression0(ctx.expression0());
            } else if (ctx.designator() != null) {
                return expressionVisitor.visitDesignator(ctx.designator());
            } else if (ctx.callExpression() != null) {
                return expressionVisitor.visitCallExpression(ctx.callExpression());
            } else if (ctx.literal() != null) {
                return expressionVisitor.visitLiteral(ctx.literal());
            } else {
                // error??
                return null;
            }
        }


        @Override
        public Call visitCallExpression(CruxParser.CallExpressionContext ctx) {
            Symbol callee = new Symbol(ctx.Identifier().getSymbol().getText());
            List<Expression> arguments = new ArrayList<>();
            for (CruxParser.Expression0Context expression0Context : ctx.expressionList().expression0()){
                arguments.add(expressionVisitor.visitExpression0(expression0Context));
            }
            return new Call(makePosition(ctx), callee, arguments);
        }


        @Override
        public Expression visitDesignator(CruxParser.DesignatorContext ctx) {
            if (ctx.expression0().size() == 0){
                Name result = new Name(makePosition(ctx), new Symbol(ctx.Identifier().getSymbol().getText()));
                if (dereferenceDesignator)
                    return new Dereference(makePosition(ctx), result);
                else
                    return result;
            } else if (ctx.expression0().size() == 1){
                Name base = new Name(makePosition(ctx), new Symbol(ctx.Identifier().getSymbol().getText()));
                Expression offset = expressionVisitor.visitExpression0(ctx.expression0(0));
                ArrayAccess result = new ArrayAccess(makePosition(ctx.expression0(0)), base, offset);
                if (dereferenceDesignator)
                    return new Dereference(makePosition(ctx), result);
                else
                    return result;
            } else {
                // todo??
                return null;
            }
        }


        @Override
        public Expression visitLiteral(CruxParser.LiteralContext ctx) {
            if (ctx.Integer() != null) {
                return new LiteralInt(makePosition(ctx), Long.parseLong(ctx.Integer().getSymbol().getText()));
            } else if (ctx.True() != null) {
                return new LiteralBool(makePosition(ctx), Boolean.parseBoolean(ctx.True().getSymbol().getText()));
            } else if (ctx.False() != null) {
                return new LiteralBool(makePosition(ctx), Boolean.parseBoolean(ctx.False().getSymbol().getText()));
            } else {
                // error?
                return null;
            }
        }

    }
}
