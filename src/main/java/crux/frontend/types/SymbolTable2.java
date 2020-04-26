//package crux.frontend.types;
//
//import crux.frontend.ast.Position;
//import crux.frontend.Symbol;
//import crux.frontend.ast.*;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//import java.util.Map;
//
//final class SymbolTable2 {
//    /**
//     * To store variable array declaration
//     */
//    private final List<Map<String, Node>> symbolScopes = new ArrayList<>();
//
//    private boolean encounteredError = false;
//
//    SymbolTable2() {
////        this.err = err;
//        Map<String, Symbol> firstScope = new HashMap<>();
//        // this first scope will be our global scope. We initialize it with predefined functions:
//        // readInt
//        FuncType readIntFuncType = new FuncType(new TypeList(), new IntType());
//        firstScope.put("readInt", new Symbol("readInt", readIntFuncType));
//        // printBool
//        TypeList printBoolTypeList = new TypeList();
//        printBoolTypeList.append(new BoolType());
//        FuncType printBoolFuncType = new FuncType(printBoolTypeList, new VoidType());
//        firstScope.put("printBool", new Symbol("printBool", printBoolFuncType));
//        // printInt
//        TypeList printIntTypeList = new TypeList();
//        printIntTypeList.append(new IntType());
//        FuncType printIntFuncType = new FuncType(printIntTypeList, new VoidType());
//        firstScope.put("printInt", new Symbol("printInt", printIntFuncType));
//        // println
//        FuncType printlnFuncType = new FuncType(new TypeList(), new VoidType());
//        firstScope.put("println", new Symbol("println", printlnFuncType));
//        symbolScopes.add(firstScope);
//    }
//
//    boolean hasEncounteredError() {
//        return encounteredError;
//    }
//
//    void enter() {  // enter a scope --> make a new symboltable???
//        Map<String, Symbol> newScope = new HashMap<>();
//        symbolScopes.add(newScope);
//    }
//
//    void exit() {  // exit a scope --> pop the stack?
//        symbolScopes.remove(symbolScopes.size()-1);
//    }
//
//    Symbol add(Position pos, String name) {
//        if (symbolScopes.get(symbolScopes.size()-1).containsKey(name)){
////            err.printf("DeclareSymbolError%s[%s already exists.]%n", pos, name);
//            encounteredError = true;
//            return new Symbol(name, "DeclareSymbolError");
//        }
//        Symbol sym = new Symbol(name);
//        symbolScopes.get(symbolScopes.size()-1).put(name, sym);
//        return sym;
//    }
//
//    Symbol add(Position pos, String name, Type type) {
//        if (symbolScopes.get(symbolScopes.size()-1).containsKey(name)){
////            err.printf("DeclareSymbolError%s[%s already exists.]%n", pos, name);
//            encounteredError = true;
//            return new Symbol(name, "DeclareSymbolError");
//        }
//        Symbol sym = new Symbol(name, type);
//        symbolScopes.get(symbolScopes.size()-1).put(name, sym);
//        return sym;
//    }
//
//    Symbol lookup(Position pos, String name) {
//        var symbol = find(name);
//        if (symbol == null) {
////            err.printf("ResolveSymbolError%s[Could not find %s.]%n", pos, name);
//            encounteredError = true;
//            return new Symbol(name, "ResolveSymbolError");
//        } else {
//            return symbol;
//        }
//    }
//
//    private Symbol find(String name) {
//        for (int i = symbolScopes.size()-1; i>=0; i--){
//            Map<String, Symbol> scope = symbolScopes.get(i);
//            if (scope.get(name) != null) return scope.get(name);
//        }
//        return null;
//    }
//}
