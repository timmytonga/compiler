package crux.midend;

import crux.frontend.Symbol;
import crux.frontend.ast.*;
import crux.frontend.ast.traversal.NodeVisitor;
import crux.frontend.types.*;
import crux.midend.ir.core.*;
import crux.midend.ir.core.insts.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lower from AST to IR
 * */
public final class ASTLower implements NodeVisitor {
    private Program mCurrentProgram = null;
    private Function mCurrentFunction = null;

    private Map<Symbol, AddressVar> mCurrentGlobalSymMap = null;
    private Map<Symbol, Variable> mCurrentLocalVarMap = null;
    private Map<String, AddressVar> mBuiltInFuncMap = null;
    private TypeChecker mTypeChecker;

    // mCurrentExpression, mLastControlInstruction
    private Instruction mLastControlInstruction;
    private Value mCurrentExpression;
  
    public ASTLower(TypeChecker checker) {
        mTypeChecker = checker;
        initBuiltInFunctions();
    }

    private void add_adge(Instruction src, Instruction dst){
        if (src == null){
            mCurrentFunction.setStart(dst);
        } else {
            src.setNext(0, dst);  // 0 unless jump function
        }
    }

    public Program lower(DeclarationList ast) {
        visit(ast);
        return mCurrentProgram;
    }

    /**
     * The top level Program
     * */
    private void initBuiltInFunctions() {
        FuncType readIntFuncType = new FuncType(new TypeList(), new IntType());

        TypeList printBoolTypeList = new TypeList();
        printBoolTypeList.append(new BoolType());
        FuncType printBoolFuncType = new FuncType(printBoolTypeList, new VoidType());

        TypeList printIntTypeList = new TypeList();
        printIntTypeList.append(new IntType());
        FuncType printIntFuncType = new FuncType(printIntTypeList, new VoidType());

        FuncType printlnFuncType = new FuncType(new TypeList(), new VoidType());

        mBuiltInFuncMap = new HashMap<>();
        mBuiltInFuncMap.put("readInt", new AddressVar(readIntFuncType, "readInt"));
        mBuiltInFuncMap.put("printBool", new AddressVar(printBoolFuncType, "printBool"));
        mBuiltInFuncMap.put("printInt", new AddressVar(printIntFuncType, "printInt"));
        mBuiltInFuncMap.put("println", new AddressVar(printlnFuncType, "println"));

    }

    @Override
    public void visit(DeclarationList declarationList) {
        mCurrentProgram = new Program();
        mCurrentGlobalSymMap = new HashMap<>();
        mCurrentLocalVarMap = new HashMap<>();
        // need to make a new mCurrentFunction, and mCurrentProgram to get everything started
        for (Node child : declarationList.getChildren()){
            mCurrentFunction = null;  // reset scope
            child.accept(this);  // so this can be a global var declaration or a function
        }
    }

    /**
     * Function
     * */
    @Override
    public void visit(FunctionDefinition functionDefinition) {
        Symbol funcDef = functionDefinition.getSymbol();
        String funcName = funcDef.getName();
        FuncType funcType = (FuncType)funcDef.getType();
        // need to setup the args
        List<LocalVar> args = new ArrayList<>();
        for (Symbol arg : functionDefinition.getParameters()){
            LocalVar localVar = new LocalVar(arg.getType(), arg.getName());
            args.add(localVar);
            mCurrentLocalVarMap.put(arg, localVar);
        }
        // need to set mCurrentFunction to the function...
        mCurrentFunction = new Function(funcName, args, funcType);
        mLastControlInstruction = null;  // we reset this since we are defining a new scope
        // this will set currentfunction's start instruction....
        functionDefinition.getStatements().accept(this);
        mCurrentProgram.addFunction(mCurrentFunction);  // finally we add the function to the program
        mCurrentGlobalSymMap.put(funcDef, new AddressVar(funcType, funcName));
    }

    @Override
    public void visit(StatementList statementList) {
        for (Node child : statementList.getChildren()){
            child.accept(this);  // this will cause mLastControlInstruction to be set
        }
    }

    /**
     * Declarations
     * */
    @Override
    public void visit(VariableDeclaration variableDeclaration) {
        String varName = variableDeclaration.getSymbol().getName();
        Type varType = variableDeclaration.getSymbol().getType();
        if(mCurrentFunction == null) {  // i.e. we are in the global scope
            // in global scope we must construct an AddressVar
            var addrVar = new AddressVar(varType, varName);
            mCurrentGlobalSymMap.put(variableDeclaration.getSymbol(), addrVar);
            // len is 0 because not an array
            IntegerConstant len = IntegerConstant.get(mCurrentProgram, 1);
            mCurrentProgram.addGlobalVar(new GlobalDecl(addrVar, len));
        } else {  // we are in a function scope
            // todo
        }
    }
  
    @Override
    public void visit(ArrayDeclaration arrayDeclaration) {
        // only in global scope
        ArrayType arrayType = (ArrayType)arrayDeclaration.getSymbol().getType();
        String arrayName = arrayDeclaration.getSymbol().getName();
        var addrVar = new AddressVar(arrayType, arrayName);
        mCurrentGlobalSymMap.put(arrayDeclaration.getSymbol(), addrVar);
        mCurrentProgram.addGlobalVar(new GlobalDecl(addrVar, IntegerConstant.get(mCurrentProgram, arrayType.getExtent())));
    }

    @Override
    public void visit(Name name) {
        Symbol sym = name.getSymbol();

        if (mCurrentLocalVarMap.get(sym) != null){  // we first look inside local scope
            mCurrentExpression = mCurrentLocalVarMap.get(sym);  // this can be local or addressvar
        } else if (mCurrentGlobalSymMap.get(sym) != null){
            AddressVar dstVar =  mCurrentFunction.getTempAddressVar(sym.getType());
            AddressAt addrAtInst = new AddressAt(dstVar, mCurrentGlobalSymMap.get(sym));
            add_adge(mLastControlInstruction, addrAtInst);
            mLastControlInstruction = addrAtInst;
            mCurrentExpression = dstVar;
        }
    }

    @Override
    public void visit(Assignment assignment) {
        Expression lhs = assignment.getLocation();
        Expression rhs = assignment.getValue();

        rhs.accept(this);  // so this will set some value to CurrentExpression
        LocalVar srcVal = (LocalVar) mCurrentExpression;
        lhs.accept(this);  // this wil get the address
        AddressVar destAddr = (AddressVar) mCurrentExpression;

        StoreInst storeInst  = new StoreInst(srcVal, destAddr);
        add_adge(mLastControlInstruction, storeInst);
        mLastControlInstruction = storeInst;

    }

    @Override
    public void visit(Call call) {
        // we need to get the AddressVar for the callee either from a user-def function or builtin
        AddressVar calleeAddress = mCurrentGlobalSymMap.get(call.getCallee());
        if (calleeAddress == null){ // must be a builtin function
            calleeAddress = mBuiltInFuncMap.get(call.getCallee().getName());
        }
        List<LocalVar> params = new ArrayList<>();
        for (Expression arg : call.getArguments()){
            arg.accept(this);  // this will add to the localVars
            // there are two cases: one it will visit literalBool or literalInt
            // the other it will visit Dereference....
            params.add((LocalVar) mCurrentExpression);
        }
        // now we make callInst
        CallInst callInst;
        FuncType funcType = (FuncType)call.getCallee().getType();
        if (funcType.getRet() instanceof VoidType){  // there's no return type
            callInst = new CallInst(calleeAddress, params);
        } else {
            LocalVar dstVar = mCurrentFunction.getTempVar(funcType.getRet());
            callInst = new CallInst(dstVar, calleeAddress, params);
            mCurrentExpression = dstVar;
        }
        add_adge(mLastControlInstruction, callInst);
        mLastControlInstruction = callInst;
    }

    @Override
    public void visit(OpExpr operation) {
        // first we process the LHS
        operation.getLeft().accept(this);
        LocalVar lhs = (LocalVar) mCurrentExpression;
        if (operation.getRight() == null){  // this is a UnaryNot operator
            LocalVar dstVar = mCurrentFunction.getTempVar(new BoolType());
            UnaryNotInst unaryNotInst = new UnaryNotInst(dstVar, lhs);
            add_adge(mLastControlInstruction, unaryNotInst);
            mLastControlInstruction = unaryNotInst;
            mCurrentExpression = dstVar;
            return;
        }  // else it's the other binary operator
        CompareInst.Predicate compPredicate = null;
        BinaryOperator.Op  binaryOp = null;
        String boolOp = null;
        switch (operation.getOp().toString()) {
            case (">="):
                compPredicate = CompareInst.Predicate.GE;
                break;
            case ("<="):
                compPredicate = CompareInst.Predicate.LE;
                break;
            case ("!="):
                compPredicate = CompareInst.Predicate.NE;
                break;
            case ("=="):
                compPredicate = CompareInst.Predicate.EQ;
                break;
            case (">"):
                compPredicate = CompareInst.Predicate.GT;
                break;
            case ("<"):
                compPredicate = CompareInst.Predicate.LT;
                break;
            case ("+"):
                binaryOp = BinaryOperator.Op.Add;
                break;
            case ("-"):
                binaryOp = BinaryOperator.Op.Sub;
                break;
            case ("*"):
                binaryOp = BinaryOperator.Op.Mul;
                break;
            case ("/"):
                binaryOp = BinaryOperator.Op.Div;
                break;
            case ("&&"):
                boolOp = "&&";
                break;
            case ("||"):
                boolOp = "||";
                break;
            default:
                break;  // should not be here???
        }

        if (boolOp != null) {  // if we are handling "and" or "or"
            // first we make a result variable
            LocalVar dstVar = mCurrentFunction.getTempVar(new BoolType());
            // we make the assignment to dstVar depending on the value of lhs
            JumpInst jumpInst = new JumpInst(lhs);
            add_adge(mLastControlInstruction, jumpInst);
            mLastControlInstruction = jumpInst;
            NopInst mergeInst = new NopInst();
            NopInst thenBranch = new NopInst();
            if (boolOp.equals("&&")){
                // if (lhs) {dstvar = rhs} else {dstvar = lhs};
                // we first handle the else block: setting dstvar = lhs;
                CopyInst copyInst = new CopyInst(dstVar, lhs);
                add_adge(jumpInst, copyInst);
                add_adge(copyInst, mergeInst);
                // now we handle the then block in which we branch away
                jumpInst.setNext(1, thenBranch);
                mLastControlInstruction = thenBranch;
                // then we process the RHS
                operation.getRight().accept(this);  // this should set the mCurrentExpression
                LocalVar rhs = (LocalVar)mCurrentExpression;
                CopyInst copyInst1 = new CopyInst(dstVar, rhs);
                add_adge(mLastControlInstruction, copyInst1);
                mLastControlInstruction = copyInst1;
            } else {  // boolOp == "||"
                // if (lhs) {dstvar = lhs} else {dstvar = rhs}
                // first handle the else block: dstvar = rhs
                operation.getRight().accept(this);  // this should set the mCurrentExpression
                LocalVar rhs = (LocalVar)mCurrentExpression;
                CopyInst copyInst = new CopyInst(dstVar, rhs);
                add_adge(mLastControlInstruction, copyInst);
                add_adge(copyInst, mergeInst);
                // now we handle the then block in which we branch away
                jumpInst.setNext(1, thenBranch);
                // in the then block we set dstvar = lhs
                CopyInst copyInst1 = new CopyInst(dstVar, lhs);
                add_adge(thenBranch, copyInst1);
                mLastControlInstruction = copyInst1;
            }
            // finally add the block to the merge inst
            add_adge(mLastControlInstruction, mergeInst);
            mLastControlInstruction = mergeInst;
            mCurrentExpression = dstVar;
            return;
        }

        // then we process the RHS
        operation.getRight().accept(this);
        LocalVar rhs = (LocalVar) mCurrentExpression;
        // then we combine them together

        if (compPredicate != null){  // we need to make a comp inst
            LocalVar dstVar = mCurrentFunction.getTempVar(new BoolType());
            CompareInst compareInst = new CompareInst(dstVar, compPredicate, lhs, rhs);
            add_adge(mLastControlInstruction, compareInst);
            mLastControlInstruction = compareInst;
            mCurrentExpression = dstVar;
        } else if (binaryOp != null){  // we need to make a binary op inst
            LocalVar dstVar = mCurrentFunction.getTempVar(new IntType());
            BinaryOperator opInst = new BinaryOperator(binaryOp, dstVar, lhs, rhs);
            add_adge(mLastControlInstruction, opInst);
            mLastControlInstruction = opInst;
            mCurrentExpression = dstVar;
        } // else what???

    }

    @Override
    public void visit(Dereference dereference) {
        dereference.getAddress().accept(this);
        if (mCurrentExpression instanceof  AddressVar) {  // must be a global var
            AddressVar srcAddr = (AddressVar)mCurrentExpression;
            LocalVar dstVar = mCurrentFunction.getTempVar(srcAddr.getType());
            LoadInst loadInst = new LoadInst(dstVar, srcAddr);
            add_adge(mLastControlInstruction, loadInst);
            mLastControlInstruction = loadInst;
            mCurrentExpression = dstVar;
        }  // else??? what if it's a local var

    }

    private void visit(Expression expression) {  // this seems useless.... circular
        expression.accept(this);
    }

    @Override
    public void visit(ArrayAccess access) {
        Symbol sym = access.getBase().getSymbol();
        ArrayType arrayType = (ArrayType)sym.getType();
        Variable dstVar =  mCurrentFunction.getTempAddressVar(arrayType.getBase());
        access.getOffset().accept(this);
        LocalVar offset = (LocalVar) mCurrentExpression;
        AddressAt addrAtInst = new AddressAt(dstVar, mCurrentGlobalSymMap.get(sym), offset);
        add_adge(mLastControlInstruction, addrAtInst);
        mLastControlInstruction = addrAtInst;
        mCurrentExpression = dstVar;
    }


    @Override
    public void visit(LiteralBool literalBool) {
        BooleanConstant boolVal = BooleanConstant.get(mCurrentProgram, literalBool.getValue());
        var destVar = mCurrentFunction.getTempVar(new BoolType());
        var copyInst = new CopyInst(destVar, boolVal);
        add_adge(mLastControlInstruction, copyInst);
        mLastControlInstruction = copyInst;
        mCurrentExpression = destVar;
    }

    @Override
    public void visit(LiteralInt literalInt) {
        IntegerConstant intVal = IntegerConstant.get(mCurrentProgram, literalInt.getValue());
        var destVar = mCurrentFunction.getTempVar(new IntType());
        var copyInst = new CopyInst(destVar, intVal);
        add_adge(mLastControlInstruction, copyInst);
        mLastControlInstruction = copyInst;
        mCurrentExpression = destVar;
    }

    @Override
    public void visit(Return ret) {
        // first we evaluate the expression
        ret.getValue().accept(this);
        if (mCurrentExpression instanceof LocalVar){
            ReturnInst returnInst = new ReturnInst((LocalVar)mCurrentExpression); // this should be the return value....
            add_adge(mLastControlInstruction, returnInst);
            mLastControlInstruction = returnInst;
        }
    }

    /**
     * Control Structures
     * */
    @Override
    public void visit(IfElseBranch ifElseBranch) {
        // first we process the condition...
        ifElseBranch.getCondition().accept(this);
        // then we get an mExpressionValue that should be a LiteralBool
        JumpInst jumpInst = new JumpInst((LocalVar)mCurrentExpression);
        add_adge(mLastControlInstruction, jumpInst);
        mLastControlInstruction = jumpInst;
        NopInst mergeInst = new NopInst();
        NopInst thenBranch = new NopInst();
        // we first handle the else block
        if (ifElseBranch.getElseBlock() != null){
            ifElseBranch.getElseBlock().accept(this);
        }  // if it's null then we do nothing
        // the basic block of the else will point to the mergeInst
        add_adge(mLastControlInstruction, mergeInst);
        // now we handle the then block in which we branch away
        jumpInst.setNext(1, thenBranch);
        mLastControlInstruction = thenBranch;
        // then we build a basic block from there
        ifElseBranch.getThenBlock().accept(this);
        // finally add the block to the merge inst
        add_adge(mLastControlInstruction, mergeInst);
        mLastControlInstruction = mergeInst;  // continue from the merge point
    }

    @Override
    public void visit(WhileLoop whileLoop) {
        // we need a point to jump back after we are done
        NopInst startWhile = new NopInst();
        add_adge(mLastControlInstruction, startWhile);
        mLastControlInstruction = startWhile;
        // first process the condition
        whileLoop.getCondition().accept(this);
        // then we jump to finish or the body
        JumpInst jumpInst = new JumpInst((LocalVar)mCurrentExpression);
        add_adge(mLastControlInstruction, jumpInst);
        NopInst whileBody = new NopInst();
        jumpInst.setNext(1, whileBody);  // if condition holds we go to whileBody
        mLastControlInstruction = whileBody;
        // which is constructed with the statements below
        whileLoop.getBody().accept(this);
        add_adge(mLastControlInstruction, startWhile); // after executing the while loop, we go back and check the condition:
        // now we take care of the else statement
        NopInst exitWhile = new NopInst();
        add_adge(jumpInst, exitWhile);
        mLastControlInstruction = exitWhile;
    }
}
