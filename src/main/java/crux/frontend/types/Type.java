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
        if (this.getClass() == IntType.class) return new IntType();
        if (this.getClass() == BoolType.class) return new BoolType();
        if (this.getClass() == AddressType.class){
            if (((AddressType) this).getBaseType().getClass() == IntType.class)
                return new IntType();
            if  (((AddressType) this).getBaseType().getClass() == BoolType.class)
                return new BoolType();
            if (((AddressType) this).getBaseType().getClass() == ArrayType.class)
                return ((ArrayType)((AddressType) this).getBaseType()).getBase();
            return new ErrorType("cannot deref " + this);
        } else if (this.getClass() == ArrayType.class){
            if (((ArrayType) this).getBase().getClass() == IntType.class)
                return new IntType();
            if  (((ArrayType) this).getBase().getClass() == BoolType.class)
                return new BoolType();
            return new ErrorType("cannot deref " + this);
        }
        return new ErrorType("cannot deref " + this);
    }

    Type index(Type that) {
        return new ErrorType("cannot index " + this + " with " + that);
    }

    Type call(Type args) {
        if (this.getClass() == FuncType.class && args.getClass() == TypeList.class){
            if(((FuncType) this).getArgs().equivalent(args)){
                return ((FuncType) this).getRet();  // return the return type of the function
            }
        }
        return new ErrorType("cannot call " + this + " using " + args);
    }

    Type assign(Type source) {
        if (this.deref().equivalent(source)) return this;
        return new ErrorType("cannot assign " + source + " to " + this);
    }

    public boolean equivalent(Type that) {
        throw new Error(this.getClass() + " should override the equivalent method.");
    }
}
