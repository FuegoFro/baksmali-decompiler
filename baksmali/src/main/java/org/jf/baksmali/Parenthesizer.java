package org.jf.baksmali;

import java.util.ArrayList;
import java.util.HashMap;

public class Parenthesizer {
    public static final HashMap<String, Integer> OPERATOR_PRECEDENCE = new HashMap<String, Integer>();

    static {
        OPERATOR_PRECEDENCE.put("*=", 1);
        OPERATOR_PRECEDENCE.put("/=", 1);
        OPERATOR_PRECEDENCE.put("+=", 1);
        OPERATOR_PRECEDENCE.put("-=", 1);
        OPERATOR_PRECEDENCE.put("%=", 1);
        OPERATOR_PRECEDENCE.put("<<=", 1);
        OPERATOR_PRECEDENCE.put(">>=", 1);
        OPERATOR_PRECEDENCE.put(">>>=", 1);
        OPERATOR_PRECEDENCE.put("&=", 1);
        OPERATOR_PRECEDENCE.put("^=", 1);
        OPERATOR_PRECEDENCE.put("|=", 1);
        OPERATOR_PRECEDENCE.put("=", 1);
        OPERATOR_PRECEDENCE.put("||", 2);
        OPERATOR_PRECEDENCE.put("&&", 3);
        OPERATOR_PRECEDENCE.put("|", 4);
        OPERATOR_PRECEDENCE.put("^", 5);
        OPERATOR_PRECEDENCE.put("&", 6);
        OPERATOR_PRECEDENCE.put("!=", 7);
        OPERATOR_PRECEDENCE.put("==", 7);
        OPERATOR_PRECEDENCE.put("instanceof", 8);
        OPERATOR_PRECEDENCE.put(">", 8);
        OPERATOR_PRECEDENCE.put(">=", 8);
        OPERATOR_PRECEDENCE.put("<", 8);
        OPERATOR_PRECEDENCE.put("<=", 8);
        OPERATOR_PRECEDENCE.put("<<", 9);
        OPERATOR_PRECEDENCE.put(">>", 9);
        OPERATOR_PRECEDENCE.put(">>>", 9);
        OPERATOR_PRECEDENCE.put("+", 10);
        OPERATOR_PRECEDENCE.put("-", 10);
        OPERATOR_PRECEDENCE.put("*", 11);
        OPERATOR_PRECEDENCE.put("/", 11);
        OPERATOR_PRECEDENCE.put("%", 11);
    }

    public static String ensureNoUnenclosedSpaces(String expression) {
        if (findUnenclosedSections(expression).size() > 0) {
            expression = "(" + expression + ")";
        }
        return expression;
    }

    public static String ensureOrderOfOperations(String operation, String left, String right) {
        try {
            if (!obeysOrderOfOperations(operation, findUnenclosedSections(left))) {
                left = "(" + left + ")";
            }
            if (!obeysOrderOfOperations(operation, findUnenclosedSections(right))) {
                right = "(" + right + ")";
            }
            return left + " " + operation + " " + right;
        } catch (NullPointerException e) {
            System.out.println("Left: " + left + " Right: " + right + " Operator: " + operation);
        }
        return "";
    }

    public static ArrayList<String> findUnenclosedSections(String expression) {
        ArrayList<String> sections = new ArrayList<String>();
        boolean inSpaceSection = false;
        int parenDepth = 0;
        char currentQuote = 0;
        int lastSpace = -1;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (currentQuote != 0) { // Ignore spaces and parens in quotes
                if (c == currentQuote && expression.charAt(i - 1) != '\\') {
                    currentQuote = 0;
                }
            } else if (c == '\'' || c == '"') {
                currentQuote = c;
            } else if (c == '(') { // Ignore spaces in parens
                parenDepth++;
            } else if (c == ')') {
                parenDepth--;
            } else if (c == ' ' && parenDepth == 0) {
                if (!inSpaceSection) {
                    lastSpace = i;
                    inSpaceSection = true;
                } else {
                    sections.add(expression.substring(lastSpace + 1, i));
                    inSpaceSection = false;
                }
            }
        }

        if (inSpaceSection) {
            sections.add(null);
        }
        return sections;
    }

    public static boolean obeysOrderOfOperations(String operation, ArrayList<String> spaceSections) {
        int minPrecedence = Integer.MAX_VALUE;
        for (String section : spaceSections) {
            if (section == null) {
                return false; // Most likely a cast, should be contained in parens
            } else {
                try {
                    int precedence = OPERATOR_PRECEDENCE.get(section);
                    if (precedence < minPrecedence) {
                        minPrecedence = precedence;
                    }
                } catch (NullPointerException e) {
                    System.out.println("section: " + section);
                    throw e;
                }
            }
        }
        return minPrecedence >= OPERATOR_PRECEDENCE.get(operation);
    }
}