package org.jf.baksmali;

import org.jf.util.MemoryWriter;

import java.util.HashSet;

public class InnerClass {
    private final String body;
    private final String superClass;
    private final HashSet<String> imports;
    private final boolean isAnonymous;

    public InnerClass(MemoryWriter body, HashSet<String> imports) {
        this(body, null, imports);
    }

    public InnerClass(MemoryWriter body, String superClass, HashSet<String> imports) {
        this.body = body.getContents();
        this.superClass = superClass;
        this.imports = imports;
        isAnonymous = (superClass != null);

    }

    public String getBody() {
        return body;
    }

    public HashSet<String> getImports() {
        return imports;
    }

    public String getSuperClass() {
        return superClass;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }
}
