package org.cunmin18.breadzero.utils.bilibili.dynamic;

public class ErrorBuilder {
    public static String build(String reason,String addition){
        return "[ERROR]" + reason + " | " + addition;
    }
    public static String build(String reason){
        return "[ERROR]" + reason;
    }
}
