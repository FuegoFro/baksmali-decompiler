package org.jf.baksmali.Adaptors;

import org.jf.dexlib.TypeIdItem;

public class TypeFormatter {
    public static String getType(TypeIdItem typeIdItem) {
        return getType(typeIdItem.getTypeDescriptor());
    }

    public static String getType(String unformattedType) {
        String formattedType = parseType(unformattedType);
        ClassDefinition.addImport(formattedType);
        return shortenType(formattedType);
    }

    public static String getFullType(TypeIdItem typeIdItem) {
        return parseType(typeIdItem.getTypeDescriptor());
    }

    private static String shortenType(String javaType) {
        int lastPeriod = javaType.lastIndexOf('.');
        if (lastPeriod >= 0) {
            javaType = javaType.substring(lastPeriod + 1);
        }
        return javaType;
    }

    private static String parseType(String typeString) {
        if (typeString.charAt(0) == 'L' | typeString.charAt(0) == 'T') {
            int length = typeString.length();
            if (typeString.charAt(length - 1) == ';') {
                length--;
            }
            typeString = typeString.substring(1, length).replace("/", ".").replace("$", ".");
        } else if(typeString.charAt(0) == '[') {
            typeString = parseType(typeString.substring(1)) + "[]";
        } else if(typeString.equals("V")) {
            typeString = "void";
        } else if(typeString.equals("Z")) {
            typeString = "boolean";
        } else if(typeString.equals("B")) {
            typeString = "byte";
        } else if(typeString.equals("S")) {
            typeString = "short";
        } else if(typeString.equals("C")) {
            typeString = "char";
        } else if(typeString.equals("I")) {
            typeString = "int";
        } else if(typeString.equals("J")) {
            typeString = "long";
        } else if(typeString.equals("F")) {
            typeString = "float";
        } else if(typeString.equals("D")) {
            typeString = "double";
        }
        return typeString;
    }
}
