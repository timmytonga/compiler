package crux.frontend;

import crux.frontend.ast.Position;
import crux.frontend.types.*;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SymbolTable {
    private final PrintStream err;
    private final List<Map<String, Symbol>> symbolScopes = new ArrayList<>();

    private boolean encounteredError = false;

    SymbolTable(PrintStream err) {
        this.err = err;
        Map<String, Symbol> firstScope = new HashMap<>();
        // this first scope will be our global scope. We initialize it with predefined functions:
        firstScope.put("readInt", new Symbol("readInt"));
        firstScope.put("printBool", new Symbol("printBool"));
        firstScope.put("printInt", new Symbol("printInt"));
        firstScope.put("println", new Symbol("println"));
        symbolScopes.add(firstScope);
    }

    boolean hasEncounteredError() {
        return encounteredError;
    }

    void enter() {  // enter a scope --> make a new symboltable???
        Map<String, Symbol> newScope = new HashMap<>();
        symbolScopes.add(newScope);
    }

    void exit() {  // exit a scope --> pop the stack?
        symbolScopes.remove(symbolScopes.size()-1);
    }

    Symbol add(Position pos, String name) {
        if (symbolScopes.get(symbolScopes.size()-1).containsKey(name)){
            err.printf("DeclareSymbolError%s[%s already exists.]%n", pos, name);
            encounteredError = true;
            return new Symbol(name, "DeclareSymbolError");
        }
        Symbol sym = new Symbol(name);
        symbolScopes.get(symbolScopes.size()-1).put(name, sym);
        return sym;
    }

    Symbol add(Position pos, String name, Type type) {
        if (symbolScopes.get(symbolScopes.size()-1).containsKey(name)){
            err.printf("DeclareSymbolError%s[%s already exists.]%n", pos, name);
            encounteredError = true;
            return new Symbol(name, "DeclareSymbolError");
        }
        Symbol sym = new Symbol(name, type);
        symbolScopes.get(symbolScopes.size()-1).put(name, sym);
        return sym;
    }

    Symbol lookup(Position pos, String name) {
        var symbol = find(name);
        if (symbol == null) {
            err.printf("ResolveSymbolError%s[Could not find %s.]%n", pos, name);
            encounteredError = true;
            return new Symbol(name, "ResolveSymbolError");
        } else {
            return symbol;
        }
    }

    private Symbol find(String name) {
        for (int i = symbolScopes.size()-1; i>=0; i--){
            Map<String, Symbol> scope = symbolScopes.get(i);
            if (scope.get(name) != null) return scope.get(name);
        }
        return null;
    }
}
