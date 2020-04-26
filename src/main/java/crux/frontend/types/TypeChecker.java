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
        }

        @Override
        public void visit(Assignment assignment) {

        }

        @Override
        public void visit(Call call) {
            FuncType function = (FuncType)call.getCallee().getType();
            Type returnType = function.getRet();
            setNodeType(call, returnType);

            if (function.getArgs().getList().size() != call.getArguments().size()){
                addTypeError(call, "visit(Call call) call has more arguments than expected.");
            }
            // check call is valid i.e. the arg of each param matches
            for (int i = 0; i < call.getArguments().size(); i++){
                Expression arg = call.getArguments().get(i);
                arg.accept(this);
                Type argType = getType(arg);
                Type expectedType = function.getArgs().getList().get(i);
//                if (!argType.equivalent(expectedType)){
//                    addTypeError(arg, "Wrong expected arg type");
//                }
            }
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
            setNodeType(dereference, getType(child));
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
                    addTypeError(functionDefinition, "Function main has invalid signature.");
                    return;
                }
            }
            setNodeType(functionDefinition, funcType);
            for (Node child: functionDefinition.getStatements().getChildren()){
                // for each statement in the statementList in the function's body
                // we check for them
                child.accept(this);
                if (child instanceof Return){
                    if (!getType(child).toString().equals(returnType.toString())){
                        String msg = String.format("Function %s returns %s not %s.",
                                funcName, returnType.toString(), getType(child).toString());
                        addTypeError(child, msg);
                    }
                }
            }


        }

        @Override
        public void visit(IfElseBranch ifElseBranch) {
        }

        @Override
        public void visit(ArrayAccess access) {
            setNodeType(access, access.getBase().getSymbol().getType());
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
            rightChild.accept(this);
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
            setNodeType(ret, getType(child));
        }

        @Override
        public void visit(StatementList statementList) {
        }

        @Override
        public void visit(VariableDeclaration variableDeclaration) {
        }

        @Override
        public void visit(WhileLoop whileLoop) {
        }
    }
}
