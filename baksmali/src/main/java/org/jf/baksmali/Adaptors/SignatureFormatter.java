package org.jf.baksmali.Adaptors;

import org.jf.dexlib.AnnotationItem;
import org.jf.dexlib.AnnotationSetItem;
import org.jf.dexlib.EncodedValue.ArrayEncodedValue;
import org.jf.dexlib.EncodedValue.EncodedValue;
import org.jf.dexlib.EncodedValue.ValueType;
import org.jf.util.IndentingWriter;

import java.io.IOException;
import java.util.ArrayList;

public class SignatureFormatter {
    public static boolean writeSignature(IndentingWriter writer, AnnotationSetItem annotationSet, Origin origin) throws IOException {
        if (annotationSet != null) {
            for (AnnotationItem annotation : annotationSet.getAnnotations()) {
                if (annotation.getEncodedAnnotation().annotationType.getTypeDescriptor().equals("Ldalvik/annotation/Signature;")) {
                    EncodedValue[] values = annotation.getEncodedAnnotation().values;
                    if (values.length != 1) {
                        System.err.println("Signature annotation does not have exactly one value!");
                    } else if (!values[0].getValueType().equals(ValueType.VALUE_ARRAY)) {
                        System.err.println("Signature annotation has non-array value!");
                    } else {
                        EncodedValue[] signature = ((ArrayEncodedValue) values[0]).values;
                        switch (origin) {
                            case Class:
                                writer.write(parseClassSignature(signature));
                                break;
                            case Method:
                                writer.write(parseMethodSignature(signature));
                                break;
                            case Field:
                                writer.write(parseFieldSignature(signature));
                                break;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static String parseClassSignature(EncodedValue[] signatureValues) {
        StringBuilder stringBuilder = new StringBuilder();
        Signature signature = new Signature(signatureValues);
        stringBuilder.append(parseGeneric(signature));

        Signature.Token superClass = signature.getNext();
        if (!superClass.value.equals("Object;")) {
            stringBuilder.append(" extends ");
            stringBuilder.append(superClass.value);
            stringBuilder.append(parseGeneric(signature));
        }
        if (signature.hasNext()) {
            stringBuilder.append(" implements ");
            boolean first = true;

            while (signature.hasNext()) {
                if (!first) {
                    stringBuilder.append(", ");
                }
                first = false;
                stringBuilder.append(parseType(signature));
            }
        }
        return stringBuilder.toString();
    }

    public static String parseMethodSignature(EncodedValue[] signatureValues) {
        StringBuilder method = new StringBuilder();
        Signature signature = new Signature(signatureValues);
        method.append(parseGeneric(signature));
        if (method.length() > 0) {
            method.append(" ");
        }
        
        if (!(signature.hasNext() && signature.getNext().value.equals("("))) {
            throw new IllegalStateException("Improper Signature Format!");
        }

        ArrayList<String> parameterTypes = new ArrayList<String>();
        Signature.Token next = signature.getNext();
        while (signature.hasNext() && !next.value.equals(")")) {
            parameterTypes.add(next.value + parseGeneric(signature));
            next = signature.getNext();
        }
        MethodDefinition.setParameterTypes(parameterTypes);
        method.append(parseType(signature));
        return method.toString();
    }

    public static String parseFieldSignature(EncodedValue[] signatureValues) {
        return parseType(new Signature(signatureValues));
    }

    private static String parseType(Signature signature) {
        Signature.Token token = signature.getNext();
        if (!token.type.equals(Signature.TokenType.Type)) {
            throw new IllegalStateException("Improper token being parsed as type!");
        }
        return token.value + parseGeneric(signature);
    }

    public static String parseGeneric(Signature signature) {
        if (!(signature.hasNext() && signature.peek().value.equals("<"))) {
            return "";
        }
        StringBuilder generic = new StringBuilder(signature.getNext().value);

        boolean useComma = false;
        int depth = 1;
        while (signature.hasNext() && depth > 0) {
            Signature.Token next = signature.getNext();
            switch (next.type) {
                case Definition:
                case Type:
                    if (useComma) {
                        generic.append(", ");
                    }
                    useComma = true;
                    generic.append(next.value);
                    break;
                case Literal:
                    if (next.value.equals("<")) {
                        depth++;
                        useComma = false;
                    } else if (next.value.equals(">")) {
                        depth--;
                        useComma = true;
                    } else {
                        useComma = !next.value.equals(" & ");
                    }
                    generic.append(next.value);
            }
        }
        return generic.toString();
    }

    enum Origin {
        Class,
        Method,
        Field
    }
}
