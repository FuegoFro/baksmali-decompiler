package org.jf.baksmali.Adaptors;

import org.jf.dexlib.EncodedValue.EncodedValue;
import org.jf.dexlib.EncodedValue.StringEncodedValue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Signature {
    private String signature;
    private String foundValue;
    private int nextCharIndex = 0;
    private Token next;

    private static final Pattern DEFINITION = Pattern.compile("[a-zA-Z]+::?" + "\\[*[LT][^;^<^>^\\(^\\)]+;?");
    private static final Pattern LONG_TYPE = Pattern.compile("\\[*[LT][^;^<^>^\\(^\\)]+;?");
    private static final Pattern SHORT_TYPE = Pattern.compile("\\[*[VZBSCIJFD]");
    private static final Pattern SPECIAL_CHARACTER = Pattern.compile("[\\+\\-\\*]");
    private static final Pattern LITERAL_CHARACTER = Pattern.compile("[<>\\(\\)\\:]");
    private static final Pattern LITERAL_INNER_CLASS = Pattern.compile("\\.[^;^<^>^\\(^\\)^/]+;?");
    private static final Pattern IGNORED_CHARACTER = Pattern.compile(";");

    public Signature(EncodedValue[] values) {
        StringBuilder stringBuilder = new StringBuilder();
        for (EncodedValue encodedValue : values) {
            stringBuilder.append(((StringEncodedValue) encodedValue).value.getStringValue());
        }
        signature = stringBuilder.toString();
        next = findNext();
    }

    public Signature(String signature) {
        this.signature = signature;
        next = findNext();
    }

    public Token getNext() {
        Token rtn = next;
        next = findNext();
        return rtn;
    }

    public boolean hasNext() {
        return !next.type.equals(TokenType.None);
    }

    public Token peek() {
        return next;
    }

    private Token findNext() {
        if (nextCharIndex >= signature.length()) {
            return new Token();
        } else if (isNext(DEFINITION)) {
            return new Token(TokenType.Definition, getGenericTypeDefinition());
        } else if (isNext(LONG_TYPE) || isNext(SHORT_TYPE)) {
            return new Token(TokenType.Type, TypeFormatter.getType(foundValue));
        } else if (isNext(SPECIAL_CHARACTER)) {
            Token special = new Token(TokenType.Type, getSpecial());
            if (!special.value.equals("?")) {
                Token referencedType = findNext();
                if (!referencedType.type.equals(TokenType.Type)) {
                    System.err.println("Non-type found after generic wildcard! '" + special + "' '" + referencedType.value);
                    return new Token();
                }
                special.value += referencedType.value;
            }
            return special;
        } else if (isNext(LITERAL_CHARACTER) || isNext(LITERAL_INNER_CLASS)) {
            return new Token(TokenType.Literal, getLiteral());
        } else if (isNext(IGNORED_CHARACTER)) {
            return findNext();
        } else {
            System.err.println("Unknown signature value: " + signature.charAt(nextCharIndex) + " at index " + nextCharIndex + " in " + signature);
            nextCharIndex++;
            return new Token();
        }
    }

    private boolean isNext(Pattern pattern) {
        Matcher matcher = pattern.matcher(signature);
        if (matcher.find(nextCharIndex) && matcher.start() == nextCharIndex) {
            foundValue = matcher.group();
            nextCharIndex = matcher.end();
            return true;
        }
        return false;
    }

    private String getGenericTypeDefinition() {
        String[] split = foundValue.split(":");
        if (split.length == 3) {
            return split[0] + " extends " + TypeFormatter.getType(split[2]);
        } else if (split.length == 2) {
            return split[0];
        }
        System.err.println("Unknown generic type definition format: " + foundValue);
        return null;
    }

    private String getSpecial() {
        if (foundValue.equals("*")) {
            return "?";
        } else if (foundValue.equals("+")) {
            return "? extends ";
        } else if (foundValue.equals("-")) {
            return "? super ";
        } else {
            System.err.println("Unknown special character in signature: " + foundValue);
            return "";
        }
    }

    private String getLiteral() {
        if (foundValue.equals(":")) {
            return " & ";
        }
        return foundValue;
    }

    enum TokenType {
        Definition,
        Type,
        Literal,
        None
    }

    class Token {
        public TokenType type;
        public String value;

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        Token() {
            type = TokenType.None;
            value = "";
        }
    }
}
