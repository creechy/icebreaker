package org.fakebelieve;

public class OutputUtil {

    public static void iprintf(int indent, String format, Object... args) {
        System.out.print(String.format(format, args).indent(indent));
    }
}
