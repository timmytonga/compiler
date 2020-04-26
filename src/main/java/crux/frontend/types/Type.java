package crux.frontend.types;

public abstract class Type {
    Type add(Type that) {
        if (this.toString().equals("int") && that.toString().equals("int")){
            return new IntType();
        }
        return new ErrorType("cannot add " + this + " with " + that);
    }

    Type sub(Type that) {
        if (this.toString().equals("int") && that.toString().equals("int")){
            return new IntType();
        }
        return new ErrorType("cannot subtract " + this + " from " + that);
    }

    Type mul(Type that) {
        if (this.toString().equals("int") && that.toString().equals("int")){
            return new IntType();
        }
        return new ErrorType("cannot multiply " + this + " with " + that);
    }

    Type div(Type that) {
        if (this.toString().equals("int") && that.toString().equals("int")){
            return new IntType();
        }
        return new ErrorType("cannot divide " + this + " by " + that);
    }

    Type and(Type that) {
        if (this.toString().equals("bool") && that.toString().equals("bool")){
            return new BoolType();
        }
        return new ErrorType("cannot compute " + this + " and " + that);
    }

    Type or(Type that) {
        if (this.toString().equals("bool") && that.toString().equals("bool")){
            return new BoolType();
        }
        return new ErrorType("cannot compute " + this + " or " + that);
    }

    Type not() {
        if (this.toString().equals("bool"))
            return new BoolType();
        return new ErrorType("cannot negate " + this);
    }

    Type compare(Type that) {
        if (this.toString().equals("int") && that.toString().equals("int")){
            return new BoolType();
        }
        return new ErrorType("cannot compare " + this + " with " + that);
    }

    Type deref() {
        return new ErrorType("cannot deref " + this);
    }

    Type index(Type that) {
        return new ErrorType("cannot index " + this + " with " + that);
    }

    Type call(Type args) {
        return new ErrorType("cannot call " + this + " using " + args);
    }

    Type assign(Type source) {
        return new ErrorType("cannot assign " + source + " to " + this);
    }

    public boolean equivalent(Type that) {
        throw new Error(this.getClass() + "should override the equivalent method.");
    }
}
