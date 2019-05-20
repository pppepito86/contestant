package org.pesho.judge.rest;

public class FileType {

    public static boolean isAllowed(String name) {
        return isJava(name) || isCpp(name);
    }

    public static boolean isJava(String name) {
        return name.toLowerCase().endsWith(".java");
    }

    public static boolean isCpp(String name) {
        return name.toLowerCase().endsWith(".cpp");
    }

}
