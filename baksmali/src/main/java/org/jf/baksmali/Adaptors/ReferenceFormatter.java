/*
 * [The "BSD licence"]
 * Copyright (c) 2010 Ben Gruver
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.baksmali.Adaptors;

import org.jf.dexlib.*;
import org.jf.dexlib.Util.Utf8Utils;
import org.jf.util.IndentingWriter;

import java.io.IOException;

public class ReferenceFormatter {
    public static String getReference(Item item, boolean staticMethod) {
        switch (item.getItemType()) {
            case TYPE_METHOD_ID_ITEM:
                return getMethodReference((MethodIdItem) item, staticMethod);
            case TYPE_FIELD_ID_ITEM:
                return getFieldReference((FieldIdItem) item);
            case TYPE_STRING_ID_ITEM:
                return getStringReference((StringIdItem) item);
            case TYPE_TYPE_ID_ITEM:
                return getTypeReference((TypeIdItem) item);
        }
        return "";
    }

    public static String getMethodReference(MethodIdItem item, boolean isStaticReference) {
        String methodName = item.getMethodName().getStringValue();
        String descriptor = item.getContainingClass().getShortJavaTypeDescriptor();
        if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
            return descriptor;
        } else if (isStaticReference) {
            ClassDefinition.addImport(item.getContainingClass().getJavaTypeDescriptor());
            return descriptor + "." + methodName;
        }
        return methodName;
    }

    public static String getFieldReference(FieldIdItem item) {
        return item.getFieldName().getStringValue();
    }

    public static String getStringReference(StringIdItem item) {
        return '"' + Utf8Utils.escapeString(item.getStringValue()) + '"';
    }

    public static String getTypeReference(TypeIdItem item) {
        ClassDefinition.addImport(item.getJavaTypeDescriptor());
        return item.getShortJavaTypeDescriptor();
    }

    public static void writeReference(IndentingWriter writer, Item item) throws IOException {
        writer.write(getReference(item, false));
    }

    public static void writeMethodReference(IndentingWriter writer, MethodIdItem item) throws IOException {
        writer.write(getMethodReference(item, false));
    }

    public static void writeFieldReference(IndentingWriter writer, FieldIdItem item) throws IOException {
        writer.write(getFieldReference(item));
    }

    public static void writeStringReference(IndentingWriter writer, StringIdItem item) throws IOException {
        writer.write(getStringReference(item));
    }

    public static void writeTypeReference(IndentingWriter writer, TypeIdItem item) throws IOException {
        writer.write(getTypeReference(item));
    }
}
