package crux.frontend.types;

import java.lang.reflect.Array;

public final class ArrayType extends Type {
    private final Type base;
    private final long extent;

    public ArrayType(long extent, Type base) {
        this.extent = extent;
        this.base = base;
    }

    public Type getBase() {
        return base;
    }

    public long getExtent() { return extent; }

    @Override
    public String toString() {
        return String.format("array[%d,%s]", extent, base);
    }

    @Override
    public boolean equivalent(Type that) {
        if (that.getClass() == ArrayType.class){
            return ((ArrayType) that).base.getClass() == this.base.getClass();
        } else if (that.getClass() == AddressType.class){
            return this.getBase().equivalent(((AddressType) that).getBaseType());
        } else
            return that.getClass() == this.getBase().getClass();
    }
}
