package ca.baosiek.utils;

public class ToConsole {

    private static final String TAB;
    private static final String LF; // line feed
    private static final String CR; // carriage return

    static {
        TAB = "\t";
        CR = "\r";
        LF = "\n";
    }

    public static void printLine(String message, int level){
        System.out.print(TAB.repeat(level).concat(message).concat(LF));
    }

    public static void print(String message, int level){
        System.out.print(TAB.repeat(level).concat(message).concat(CR));
    }

    public static void lineFeed(){
        System.out.print(LF);
    }
}
