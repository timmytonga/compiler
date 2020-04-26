package crux.frontend.types;

public final class UnknownType extends Type {
    String s;
    public UnknownType(String unknownTypeName) {
        s = unknownTypeName;
    }
    @Override
    public String toString() {
        return String.format("Unknown type: %s", s);
    }
}
