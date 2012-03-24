package org.jf.baksmali;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {
    private static final Pattern ANONYMOUS_CLASS = Pattern.compile("\\.\\d+$");

    public static void main(String[] args) {
        Matcher matcher = ANONYMOUS_CLASS.matcher("VpnProfile.1");
        boolean matches = matcher.find();
        System.out.println(matches);
        List l = null;
        for (Object o : l) {
            System.out.println("HI");
        }
    }
}
