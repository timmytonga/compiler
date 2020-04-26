package crux.frontend.types;

public final class IntType extends Type {
    @Override
    public String toString() {
        return "int";
    }

    @Override
    public boolean equivalent(Type that) {
        if (that.getClass() == IntType.class){
            return true;
        } else if (that.getClass() == AddressType.class){
            return ((AddressType) that).getBaseType().getClass() == IntType.class;
        } else return false;
    }
}
