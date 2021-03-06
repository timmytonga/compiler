package crux.backend;

import crux.frontend.types.VoidType;
import crux.midend.ir.core.*;
import crux.midend.ir.core.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;

public final class CodeGen extends InstVisitor {
    private final IRValueFormatter irFormat = new IRValueFormatter();

    private final Program p;
    private final CodePrinter out;
    private HashMap<Instruction, String> curr_label_map;
    private Function currentFunction;

    public CodeGen(Program p) {
        this.p = p;
        // Do not change the file name that is outputted or it will
        // break the grader!
        out = new CodePrinter("a.s");

    }

    public void genCode() {
        for (Iterator<GlobalDecl> it = p.getGlobals(); it.hasNext(); ) {
            GlobalDecl globalDecl = it.next();
            out.printCode(".comm " + globalDecl.getAllocatedAddress().getName().substring(1) + ", " + ((IntegerConstant)globalDecl.getNumElement()).getValue()*8 + ", 8");
        }

        for (Iterator<Function> it = p.getFunctions(); it.hasNext();){
            Function f = it.next();
            genCode(f);
        }
        //This function should generate code for the entire program.
        out.close();
    }

    private int labelcount = 1;

    private String getNewLabel() {
        return "L" + (labelcount++);
    }

    private HashMap<String, Integer> varStackMap;
    private int numLocalVar = 1;
    private Integer getLocalVarStackPos(String varName){  // add to varStackMap if doesn't exists
        if(varStackMap.containsKey(varName)){
            return varStackMap.get(varName);
        } else {
            varStackMap.put(varName, numLocalVar);
            return numLocalVar++;
        }
    }

    /** Add .globl main to the appropriate spot **/
    private void checkMain(Function f){
        if (f.getName().equals("main")){
            out.printCode(".globl main");
        }
    }
    private void genCode(Function f) {
        curr_label_map = assignLabels(f);
        varStackMap = new HashMap<>();

        List<LocalVar> args = f.getArguments();
        int argIndex = 0;
        for (LocalVar a : args){
            int stackPos = getLocalVarStackPos(a.toString())*(-8);
            switch (argIndex){
                case(0):
                    out.bufferCode("movq %rdi, "+ stackPos + "(%rbp)");
                    break;
                case(1):
                    out.bufferCode("movq %rsi, "+ stackPos + "(%rbp)");
                    break;
                case(2):
                    out.bufferCode("movq %rdx, "+ stackPos + "(%rbp)");
                    break;
                case(3):
                    out.bufferCode("movq %rcx, "+ stackPos + "(%rbp)");
                    break;
                case(4):
                    out.bufferCode("movq %r8, "+ stackPos + "(%rbp)");
                    break;
                case(5):
                    out.bufferCode("movq %r9, "+ stackPos + "(%rbp)");
                    break;
                default:  // more than 6 args
                    int stackPosOverflowArgs = (argIndex-3)*8;
                    out.bufferCode("movq " + stackPosOverflowArgs + "(%rbp), %r10");
                    out.bufferCode("movq %r10, " + stackPos + "(%rbp)");
            }
            argIndex++;
        }
        outputCodeBody(f);  // this should add to the buffer... and increment the local var accordingly

        /* emit prologue (label, enter, variable) */
        checkMain(f);
        // first print label
        out.printLabel(f.getName() + ":");
        // next print enter
        out.printCode("enter $(8 * "+ numLocalVar + "), $0");
        // emit body and epilogue
        out.outputBuffer();
    }

    private void outputCodeBody(Function f){
        currentFunction = f;
        Stack<Instruction> tovisit = new Stack<>();
        HashSet<Instruction> discovered = new HashSet<>();
        tovisit.push(f.getStart());
        while (!tovisit.isEmpty()) {
            Instruction inst = tovisit.pop();
            if (curr_label_map.containsKey(inst)){
                out.bufferLabel(curr_label_map.get(inst) + ":");
            }
            inst.accept(this);
            Instruction first = inst.getNext(0);
            Instruction second = inst.getNext(1);

            if ((second != null) && (!discovered.contains(second))){
                tovisit.push(second);
                discovered.add(second);
            }
            if ((first != null) && (!discovered.contains(first))){
                tovisit.push(first);
                discovered.add(first);
            } else if (first != null && (tovisit.isEmpty() || first != tovisit.peek())){
                out.bufferCode("jmp " + curr_label_map.get(first));
            } else {
                // we return 0 for main
                if (f.getName().equals("main")){
                    out.bufferCode("movq $0, %rax");
                }
                // process end of function
                out.bufferCode("leave");
                out.bufferCode("ret");
            }
        }
    }

    /** Assigns Labels to any Instruction that might be the target of a
     * conditional or unconditional jump. */

    private HashMap<Instruction, String> assignLabels(Function f) {
        HashMap<Instruction, String> labelMap = new HashMap<>();
        Stack<Instruction> tovisit = new Stack<>();
        HashSet<Instruction> discovered = new HashSet<>();
        tovisit.push(f.getStart());
        while (!tovisit.isEmpty()) {
            Instruction inst = tovisit.pop();

            for (int childIdx = 0; childIdx < inst.numNext(); childIdx++) {
                Instruction child = inst.getNext(childIdx);
                if (discovered.contains(child)) {
                    //Found the node for a second time...need a label for merge points
                    if (!labelMap.containsKey(child)) {
                        labelMap.put(child, getNewLabel());
                    }
                } else {
                    discovered.add(child);
                    tovisit.push(child);
                    //Need a label for jump targets also
                    if (childIdx == 1 && !labelMap.containsKey(child)) {
                        labelMap.put(child, getNewLabel());
                    }
                }
            }
        }
        return labelMap;
    }

    public void visit(AddressAt i) {
        out.bufferCode("/* AddressAt */");
        AddressVar dstVar = i.getDst();
        int dstVarPos = getLocalVarStackPos(dstVar.getName())*(-8);
        AddressVar srcVar = i.getBase();
        LocalVar offSet = i.getOffset();
        if (offSet == null){  // not an array
            out.bufferCode("# no offset ");
            out.bufferCode("movq " + srcVar.getName().substring(1) + "@GOTPCREL(%rip), %r11");
            out.bufferCode("movq %r11, " + dstVarPos + "(%rbp)");
        } else{
            out.bufferCode("# there is offset... first load it");
            int offSetPos = getLocalVarStackPos(offSet.getName())*(-8);
            out.bufferCode("movq " + offSetPos + "(%rbp), %r11");
            out.bufferCode("movq $8, %r10");
            out.bufferCode("imul %r10, %r11");
            out.bufferCode("movq " + srcVar.getName().substring(1) + "@GOTPCREL(%rip), %r10");
            out.bufferCode("addq %r10, %r11");
            out.bufferCode("movq %r11, " + dstVarPos + "(%rbp)");
        }
    }

    public void visit(BinaryOperator i) {
        String opStr;
        switch (i.getOperator()){
            case Add: opStr = "add"; break;
            case Sub: opStr = "sub"; break;
            case Div: opStr = "idiv"; break;
            case Mul: opStr = "imul"; break;
            default: opStr = "ERROR"; break;
        }
        out.bufferCode("/* BinaryOperator: "+ opStr +"*/");

        if (opStr.equals("idiv")){  // differently has to do with rdx and rax
            return;
        }
        // first we load the left operand to %r10
        LocalVar leftOperand = i.getLeftOperand();
        int leftOperandStackPos = getLocalVarStackPos(leftOperand.getName())*(-8);
        out.bufferCode("movq "+ leftOperandStackPos + "(%rbp), %r10");
        // similarly for right operand to %r11
        LocalVar rightOperand = i.getRightOperand();
        int rightOperandStackPos = getLocalVarStackPos(rightOperand.getName())*(-8);
        out.bufferCode("movq "+ rightOperandStackPos + "(%rbp), %r11");
        // then we perform the operation with %r10 as destination (for subtraction)
        out.bufferCode(opStr + " %r11, %r10");
        // then store the result to the dstVar stack position
        LocalVar dstVar = i.getDst();
        int dstVarStackPos = getLocalVarStackPos(dstVar.getName())*(-8);
        out.bufferCode("movq %r10, "+ dstVarStackPos + "(%rbp)");
    }

    public void visit(CompareInst i) {
        String predicate;
        switch (i.getPredicate()){
            case GE:
                predicate = "ge";
                break;
            case GT:
                predicate = "g";
                break;
            case LE:
                predicate = "le";
                break;
            case LT:
                predicate = "l";
                break;
            case EQ:
                predicate = "e";
                break;
            case NE:
                predicate = "ne";
                break;
            default: predicate = "error";
        }
        out.bufferCode("/* CompareInst: "+ predicate +"*/");
        int leftOperandStackPos = getLocalVarStackPos(i.getLeftOperand().getName())*(-8);
        out.bufferCode("movq " + leftOperandStackPos + "(%rbp), %r10");
        int rightOperandStackPos = getLocalVarStackPos(i.getRightOperand().getName())*(-8);
        out.bufferCode("movq " + rightOperandStackPos + "(%rbp), %r11");
        out.bufferCode("cmp %r11, %r10");  // compare left with right
        out.bufferCode("movq $0, %r11");
        out.bufferCode("movq $1, %r10");
        out.bufferCode("cmov" + predicate + " %r10, %r11");
        int dstVarStackPos = getLocalVarStackPos(i.getDst().getName())*(-8);
        out.bufferCode("movq %r11, " + dstVarStackPos + "(%rbp)");
    }

    public void visit(CopyInst i) {
        out.bufferCode("/* CopyInst: */");
        LocalVar dstVar = i.getDstVar();
        int dstVarStackPos = getLocalVarStackPos(dstVar.toString())*(-8);
        // now we process srcval
        Value srcVal = i.getSrcValue();
        String srcStr = "error in copyInst (this should've been initialized)";
        if (srcVal instanceof BooleanConstant){
            if (((BooleanConstant) srcVal).getValue()){ srcStr = "$1"; }
            else { srcStr = "$0"; }
        } else if (srcVal instanceof IntegerConstant){
            srcStr = "$" + ((IntegerConstant) srcVal).getValue();
        } else if (srcVal instanceof LocalVar){
            // first load it to a temp and then we use that instead.
            out.bufferCode("movq "+getLocalVarStackPos(((LocalVar) srcVal).getName())*(-8)+ "(%rbp), %r10");
            srcStr = "%r10";
        } else if (srcVal instanceof AddressVar){
//            srcStr = ((AddressVar) srcVal).getName().substring(1) + "@GOTPCREL(%rip)";
            srcStr = "This should not happen???";
        }
//        else {
//            // ??
//        }
        out.bufferCode("movq " + srcStr + ", " + dstVarStackPos + "(%rbp)");
    }

    public void visit(JumpInst i) {
        String jumpDst = curr_label_map.get(i.getNext(1));
        out.bufferCode("/* Jumpinst */");
        int predicateStackPos = getLocalVarStackPos(i.getPredicate().getName())*(-8);
        out.bufferCode("movq " + predicateStackPos + "(%rbp), %r10");
        out.bufferCode("movq $1, %r11");
        out.bufferCode("cmp %r10, %r11");
        out.bufferCode("je " + jumpDst);
    }

    public void visit(LoadInst i) {
        out.bufferCode("/* LoadInst */");
        LocalVar dstVar = i.getDst();
        int dstVarStackPos = getLocalVarStackPos(dstVar.getName())*(-8);
        AddressVar srcAddressVar = i.getSrcAddress();
        int srcAddrVarStackPos = getLocalVarStackPos(srcAddressVar.getName())*(-8);
        out.bufferCode("movq " + srcAddrVarStackPos + "(%rbp), %r10");
        out.bufferCode("movq (%r10), %r11");
        out.bufferCode("movq %r11, " + dstVarStackPos + "(%rbp)");
    }

    public void visit(NopInst i) {
        out.bufferCode("/* NopInst */");
    }

    public void visit(StoreInst i) {
        out.bufferCode("/* StoreInst */");
        LocalVar srcVal = i.getSrcValue();
        int srcValStackPos = getLocalVarStackPos(srcVal.getName())*(-8);
        AddressVar dstAddr = i.getDestAddress();
        int dstAddrStackPos = getLocalVarStackPos(dstAddr.getName())*(-8);
        out.bufferCode("movq " + dstAddrStackPos + "(%rbp), %r10");
        out.bufferCode("movq " + srcValStackPos + "(%rbp), %r11");
        out.bufferCode("movq %r11, (%r10)");
    }

    public void visit(ReturnInst i) {
        out.bufferCode("/* ReturnInst */");
        int returnValStackPos = getLocalVarStackPos(i.getReturnValue().getName())*(-8);
        out.bufferCode("movq " + returnValStackPos + "(%rbp), %rax");
        out.bufferCode("leave");
        out.bufferCode("ret");
    }

    private int getOverflowParamPos(){
        return (numLocalVar++)*(-8);  // does this affect my localVarStack??
    }
    public void addOverflowParam(List<Value> valueList){
        if (valueList.size() > 6){
            for (int i = valueList.size() - 1; i > 5; i--){
                Value param = valueList.get(i);
                int stackPos = getLocalVarStackPos(param.toString())*(-8);  // WARNING: assuming it's a LocalVar with a pos on stack
                out.bufferCode("movq " + stackPos + "(%rbp), %r10");
                out.bufferCode("movq %r10, " + getOverflowParamPos() + "(%rbp)");
            }
        }
    }
    public void visit(CallInst i) {
        out.bufferCode("/* CallInst */");
        String funcName = i.getCallee().getName().substring(1);
        List<Value> paramList = i.getParams();
        // gotta load the params into the appropriate registers first before calling
        int paramIndex = 0;
        for (Value param : paramList){
            int stackPos = getLocalVarStackPos(param.toString())*(-8);  // WARNING: assuming it's a LocalVar with a pos on stack
            switch (paramIndex){
                case(0):  // rdi
                    out.bufferCode("movq " + stackPos + "(%rbp)" + ", %rdi");
                    break;
                case(1):  // rsi
                    out.bufferCode("movq " + stackPos + "(%rbp)" + ", %rsi");
                    break;
                case(2):  // rdx
                    out.bufferCode("movq " + stackPos + "(%rbp)" + ", %rdx");
                    break;
                case(3):  // rcx
                    out.bufferCode("movq " + stackPos + "(%rbp)" + ", %rcx");
                    break;
                case(4):  // r8
                    out.bufferCode("movq " + stackPos + "(%rbp)" + ", %r8");
                    break;
                case(5):  // r9
                    out.bufferCode("movq " + stackPos + "(%rbp)" + ", %r9");
                    break;
                default:  // more than 6 params --> we push on top of stack
                    break;
//                    int pos = getOverflowParamPos();
//                    out.bufferCode("movq " + stackPos + "(%rbp), %r10");
//                    out.bufferCode("movq %r10, " + pos + "(%rbp)");
//                    //out.bufferCode("movq " + stackPos + "(%rbp)" + ", "+ getOverflowParamPos() +"(%rbp)");
            }
            paramIndex++;
        }
        addOverflowParam(paramList);
        // now after loading the params, we call the function
        out.bufferCode("call "+ funcName);
        // now we handle return
        if (i.getDst() != null){
            String getCallVal = "movq %rax, " + getLocalVarStackPos(i.getDst().toString())*(-8) + "(%rbp)";
            out.bufferCode(getCallVal);
        }
    }

    public void visit(UnaryNotInst i) {
        out.bufferCode("/* UnaryNotInst */");
        int dstVarStackPos = getLocalVarStackPos(i.getDst().getName())*(-8);
        int innerStackPos = getLocalVarStackPos(i.getInner().getName())*(-8);
        out.bufferCode("movq " + innerStackPos + "(%rbp), %r10");
        out.bufferCode("not %r10");
        out.bufferCode("movq %r10, " + dstVarStackPos + "(%rbp)");
    }
}
