package org.jf.baksmali.Adaptors;

import org.jf.dexlib.TypeIdItem;

public class TypeFormatter {
    public static String getType(TypeIdItem typeIdItem) {
        return getType(typeIdItem.getTypeDescriptor());
    }

    public static String getType(String unformattedType) {
        String formattedType = parseType(unformattedType);
        String toImport = formattedType;
        int innerClassBoundry = formattedType.indexOf('$');
        if (innerClassBoundry > 0) {
            toImport = formattedType.substring(0, innerClassBoundry);
        }
        ClassDefinition.addImport(toImport);

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
        return javaType.replace("$", ".");
    }

    private static String parseType(String typeString) {
        if (typeString.charAt(0) == 'L' | typeString.charAt(0) == 'T') {
            int length = typeString.length();
            if (typeString.charAt(length - 1) == ';') {
                length--;
            }
            typeString = typeString.substring(1, length).replace("/", ".");
        } else if (typeString.charAt(0) == '[') {
            typeString = parseType(typeString.substring(1)) + "[]";
        } else if (typeString.equals("V")) {
            typeString = "void";
        } else if (typeString.equals("Z")) {
            typeString = "boolean";
        } else if (typeString.equals("B")) {
            typeString = "byte";
        } else if (typeString.equals("S")) {
            typeString = "short";
        } else if (typeString.equals("C")) {
            typeString = "char";
        } else if (typeString.equals("I")) {
            typeString = "int";
        } else if (typeString.equals("J")) {
            typeString = "long";
        } else if (typeString.equals("F")) {
            typeString = "float";
        } else if (typeString.equals("D")) {
            typeString = "double";
        }
        return typeString;
    }

    //Todo: Possibly remove this method
    public static boolean looksLikeDalvikType(String type) {
        if (type == null) {
            return false;
        }
        char c = type.charAt(0);
        return !type.contains(".") &&
                (c == 'L' ||
                c == 'T' ||
                c == '[' ||
                c == 'Z' ||
                c == 'B' ||
                c == 'S' ||
                c == 'C' ||
                c == 'I' ||
                c == 'J' ||
                c == 'F' ||
                c == 'D' ||
                c == 'V');
    }

    public static String zeroAs(String type) {
        if (type == null) {
            return "0";
        }
        char shorty = type.charAt(0);
        switch (shorty) {
            case 'L':
            case '[':
            case 'T':
                return "null";
            case 'Z':
                return "false";
            default:
                return "0";
        }
    }

    public static String oneAs(String type) {
        if (type != null && type.charAt(0) == 'Z') {
            return "true";
        } else {
            return "1";
        }
    }
}
