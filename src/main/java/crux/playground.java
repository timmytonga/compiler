package crux;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;



public class playground {
    public static void main(String[] args) {
        System.out.println("hello world");
        var outStream = new ByteArrayOutputStream();
        var outPrintStream = new PrintStream(outStream);
        var driver = new Driver(outPrintStream, outPrintStream);

    }
}

