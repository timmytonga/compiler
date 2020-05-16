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
            args.add(new LocalVar(arg.getType(), arg.getName()));
        }
        // need to set mCurrentFunction to the function...
        mCurrentFunction = new Function(funcName, args, funcType);
        mLastControlInstruction = null;  // we reset this since we are defining a new scope
        // this will set currentfunction's start instruction....
        functionDefinition.getStatements().accept(this);
        mCurrentProgram.addFunction(mCurrentFunction);  // finally we add the function to the program

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
    }

    @Override
    public void visit(Name name) {
        Symbol sym = name.getSymbol();
        AddressVar dstVar =  mCurrentFunction.getTempAddressVar(sym.getType());

        if (mCurrentLocalVarMap.get(sym) != null){  // we first look inside local scope
           Variable localVar = mCurrentLocalVarMap.get(sym);  // this can be local or addressvar
        } else if (mCurrentGlobalSymMap.get(sym) != null){
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
    }

    @Override
    public void visit(Dereference dereference) {
        dereference.getAddress().accept(this);
        AddressVar srcAddr = (AddressVar)mCurrentExpression;
        LocalVar dstVar = mCurrentFunction.getTempVar(srcAddr.getType());
        LoadInst loadInst = new LoadInst(dstVar, srcAddr);
        add_adge(mLastControlInstruction, loadInst);
        mLastControlInstruction = loadInst;
        mCurrentExpression = dstVar;
//
//        AddressVar destVar = ;
//        AddressVar base = ;
//        new AddressAt(destVar, base);
    }

    private void visit(Expression expression) {
        expression.accept(this);
    }

    @Override
    public void visit(ArrayAccess access) {
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
    }

    /**
     * Control Structures
     * */
    @Override
    public void visit(IfElseBranch ifElseBranch) {
    }

    @Override
    public void visit(WhileLoop whileLoop) {
    }
}
