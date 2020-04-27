package crux.frontend.types;

import crux.frontend.Symbol;
import crux.frontend.ast.*;
import crux.frontend.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TypeChecker {
    private final HashMap<Node, Type> typeMap = new HashMap<>();
    private final ArrayList<String> errors = new ArrayList<>();

    public ArrayList<String> getErrors() {
        return errors;
    }

    public void check(DeclarationList ast) {
        var inferenceVisitor = new TypeInferenceVisitor();
        inferenceVisitor.visit(ast);
    }

    private void addTypeError(Node n, String message) {
        errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
    }

    private void setNodeType(Node n, Type ty) {
        typeMap.put(n, ty);
        if (ty.getClass() == ErrorType.class) {
            var error = (ErrorType) ty;
            addTypeError(n, error.getMessage());
        }
    }

    /** 
      *  Returns type of given AST Node.
      */
  
    public Type getType(Node n) {
        return typeMap.get(n);
    }

    private final class TypeInferenceVisitor extends NullNodeVisitor {
        private Symbol currentFunctionSymbol;
        private Type currentFunctionReturnType;
        private boolean lastStatementReturns;

        @Override
        public void visit(Name name) {
            setNodeType(name, name.getSymbol().getType());
        }

        @Override
        public void visit(ArrayDeclaration arrayDeclaration) {
            ArrayType arrayType = (ArrayType)arrayDeclaration.getSymbol().getType();
            Type baseType = arrayType.getBase();
            if (baseType.getClass() == VoidType.class) {
                String arrayName = arrayDeclaration.getSymbol().getName();
                String msg = String.format("Array %s has invalid base type %s.", arrayName, baseType.toString());
                setNodeType(arrayDeclaration, new ErrorType(msg));
            } else
                setNodeType(arrayDeclaration, arrayType);
        }

        @Override
        public void visit(Assignment assignment) {
            Node left = assignment.getLocation();
            Node right = assignment.getValue();
            left.accept(this);
            right.accept(this);
            Type rightType = getType(right);
            Type leftType = getType(left);
            setNodeType(assignment, leftType.assign(rightType));
        }

        /**
         * For a call expression we must ensure the following:
         *  1. Set the return type according to the function return type
         *  2. Check the arguments are deref and type match properly.
         */
        @Override
        public void visit(Call call) {
            FuncType funcionType = (FuncType)call.getCallee().getType();
            // first we check if the call arguments are valid
            TypeList callArgs = new TypeList();
            for (Expression arg : call.getArguments()){
                // checking arguments type
                arg.accept(this);
                callArgs.append(getType(arg));
            }
            setNodeType(call, funcionType.call(callArgs));
        }

        @Override
        public void visit(DeclarationList declarationList) {  // we start here
            for (Node child :declarationList.getChildren()){
                child.accept(this);
            }
        }

        @Override
        public void visit(Dereference dereference) {
            Node child = dereference.getAddress();
            child.accept(this);
            setNodeType(dereference, getType(child).deref());
        }

        @Override
        public void visit(FunctionDefinition functionDefinition) {
            // functionDefinition.getParameters();
            Symbol funcDef = functionDefinition.getSymbol();
            String funcName = funcDef.getName();
            FuncType funcType = (FuncType)funcDef.getType();
            Type returnType = funcType.getRet();
            TypeList argTypes = funcType.getArgs();
            // we first check for main
            if (funcName.equals("main")){
                if ( !returnType.toString().equals("void") ||
                !argTypes.isEmpty()) {
                    setNodeType(functionDefinition, new ErrorType("Function main has invalid signature."));
                    return;
                }
            }
            // then we check for arguments for valid type (cannot be void
            int argPos = 0;
            for (Symbol arg : functionDefinition.getParameters()){
                if (arg.getType().toString().equals("void")){
                    String errorMsg = String.format("Function %s has a void argument in position %d.", funcName, argPos);
                    setNodeType(functionDefinition, new ErrorType(errorMsg));
                } else if (!(arg.getType().toString().equals("int") || arg.getType().toString().equals("bool"))){
                    String errorMsg = String.format("Function %s has an error in argument in position %d: %s.",
                            funcName, argPos, arg.getType().toString());
                    setNodeType(functionDefinition, new ErrorType(errorMsg));
                }
                argPos++;
            }
            // Now we check for valid return path
            boolean theresNoReturnStatement = true;
            for (Node child: functionDefinition.getStatements().getChildren()){
                // for each statement in the statementList in the function's body we check for them
                child.accept(this);
                if (child instanceof Return){
                    if (!getType(child).toString().equals(returnType.toString())){
                        String msg = String.format("Function %s returns %s not %s.",
                                funcName, returnType.toString(), getType(child).toString());
                        setNodeType(child, new ErrorType(msg));
                    }
                    theresNoReturnStatement = false;
                }
            }
            if (theresNoReturnStatement && !returnType.toString().equals("void")){
                String msg = String.format("Not all code paths in function %s return a value.", funcName);
                setNodeType(functionDefinition, new ErrorType(msg));
            }
        }

        @Override
        public void visit(IfElseBranch ifElseBranch) {
            Node condition = ifElseBranch.getCondition();
            condition.accept(this);  // typecheck condition
            if (getType(condition).getClass() != BoolType.class){
                String msg = String.format("IfElseBranch requires bool condition not %s.", getType(condition));
                setNodeType(ifElseBranch, new ErrorType(msg));
            }
            // now type check body
            StatementList thenBlock = ifElseBranch.getThenBlock();
            StatementList elseBlock = ifElseBranch.getElseBlock();
            thenBlock.accept(this);
            elseBlock.accept(this);
        }

        @Override
        public void visit(ArrayAccess access) {
            if (access.getOffset() == null){
                setNodeType(access, access.getBase().getSymbol().getType());
            } else {
                Node offSet = access.getOffset();
                offSet.accept(this);
                setNodeType(access, access.getBase().getSymbol().getType().index(getType(offSet)));
            }

        }

        @Override
        public void visit(LiteralBool literalBool) {
            setNodeType(literalBool, new BoolType());
        }

        @Override
        public void visit(LiteralInt literalInt) {
            setNodeType(literalInt, new IntType());
        }

        @Override
        public void visit(OpExpr op) {
            Node leftChild = op.getLeft();
            Node rightChild = op.getRight();
            leftChild.accept(this);
            if (rightChild != null) rightChild.accept(this);
            // first we check if left child op right child is valid.
            // if it is, the type of the opexpr is the type of the op
            switch (op.getOp().toString()){
                case (">="):
                case ("<="):
                case ("!="):
                case ("=="):
                case (">"):
                case ("<"):
                    setNodeType(op, getType(leftChild).compare(getType(rightChild)));
                    break;
                case ("+"):
                    setNodeType(op,getType(leftChild).add(getType(rightChild)));
                    break;
                case("-"):
                    setNodeType(op,getType(leftChild).sub(getType(rightChild)));
                    break;
                case("*"):
                    setNodeType(op,getType(leftChild).mul(getType(rightChild)));
                    break;
                case("/"):
                    setNodeType(op,getType(leftChild).div(getType(rightChild)));
                    break;
                case("&&"):
                    setNodeType(op,getType(leftChild).and(getType(rightChild)));
                    break;
                case("||"):
                    setNodeType(op,getType(leftChild).or(getType(rightChild)));
                    break;
                case("!"):
                    setNodeType(op,getType(leftChild).not());
                    break;
                default:
                    setNodeType(op, new ErrorType("Weird error...Couldn't find operation"));
            }
        }

        @Override
        public void visit(Return ret) {
            Node child = ret.getValue();
            child.accept(this);
            if (getType(child) instanceof ErrorType){
                typeMap.put(ret, getType(child));
            } else setNodeType(ret, getType(child));
        }

        @Override
        public void visit(StatementList statementList) {
            for (Node child : statementList.getChildren()){
                child.accept(this);
            }
        }

        @Override
        public void visit(VariableDeclaration variableDeclaration) {
            if (variableDeclaration.getSymbol().getType().getClass() == VoidType.class){
                String msg = String.format("Variable %s has invalid type void.", variableDeclaration.getSymbol().getName());
                setNodeType(variableDeclaration, new ErrorType(msg));
            } else
                setNodeType(variableDeclaration, variableDeclaration.getSymbol().getType());
        }

        @Override
        public void visit(WhileLoop whileLoop) {
            Node condition = whileLoop.getCondition();
            condition.accept(this);  // typecheck condition
            if (getType(condition).getClass() != BoolType.class){
                String msg = String.format("WhileLoop requires bool condition not %s.", getType(condition));
                setNodeType(whileLoop, new ErrorType(msg));
            }
            // now type check body
            StatementList body = whileLoop.getBody();
            body.accept(this);
        }
    }
}
