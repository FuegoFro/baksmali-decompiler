/*
 * [The "BSD licence"]
 * Copyright (c) 2010 Ben Gruver (JesusFreke)
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

import org.jf.baksmali.Adaptors.EncodedValue.EncodedValueAdaptor;
import org.jf.baksmali.InnerClass;
import org.jf.dexlib.*;
import org.jf.dexlib.Code.Analysis.ValidationException;
import org.jf.dexlib.EncodedValue.*;
import org.jf.dexlib.Util.AccessFlags;
import org.jf.dexlib.Util.SparseArray;
import org.jf.util.IndentingWriter;
import org.jf.util.MemoryWriter;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassDefinition {
    public static final String LJAVA_LANG_OBJECT = "Ljava/lang/Object;";
    private ClassDefItem classDefItem;
    private ClassDataItem classDataItem;

    private SparseArray<AnnotationSetItem> methodAnnotationsMap;
    private SparseArray<AnnotationSetItem> fieldAnnotationsMap;
    private SparseArray<AnnotationSetRefList> parameterAnnotationsMap;

    protected boolean validationErrors;

    private HashMap<String, String> staticFieldInitialValues = new HashMap<String, String>();
    private ArrayList<String> staticBlock = new ArrayList<String>();

    private static HashSet<String> imports = null;
    private static String dalvikClassName = "";
    private static String javaClassName = "";
    private static String superClass = "";
    private int innerClassAccessFlags = 0;
    private static boolean isInnerClass = false;
    private static boolean isAnonymous = false;
    private static boolean isInterface;
    private static boolean isEnum;

    private static final Pattern ENUM_VALUE_MATCHER = Pattern.compile("[ ]*(.+) = .+\\(\".+\", \\d+(, .+)?\\);");
    private static final Pattern ENUM_IS_DEFAULT_CONSTRUCTOR = Pattern.compile(".+\\(String p1, int p2\\) \\{\\n[ ]*super\\(p1, p2\\);\\n[ ]*return;\\n[ ]*\\n[ ]*\\}");
    private static final Pattern ENUM_CONSTRUCTOR_DECLARATION = Pattern.compile("(.+)\\(String p1, int p2(, .+)?\\) \\{");
    private static final Pattern STATIC_FIELD_INITAL_VALUE = Pattern.compile("^([ ]*)([^ ]+) = ([^\n]+(\\{.+\\1\\})?);\n", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern STATIC_BLOCK_CONTENTS = Pattern.compile("([^\n]*)\n");


    // Stores inner classes in memory to be added to enclosing classes
    // Key is dalvik type of inner class, value is class contents
    private static HashMap<String, InnerClass> innerClasses = new HashMap<String, InnerClass>();
    private List<TypeIdItem> interfaces;

    public ClassDefinition(ClassDefItem classDefItem) {
        this.classDefItem = classDefItem;
        this.classDataItem = classDefItem.getClassData();
        buildAnnotationMaps();
        parseClassDetails();
        findStaticFieldInitializers();
    }

    public boolean hadValidationErrors() {
        return validationErrors;
    }

    private void buildAnnotationMaps() {
        AnnotationDirectoryItem annotationDirectory = classDefItem.getAnnotations();
        if (annotationDirectory == null) {
            methodAnnotationsMap = new SparseArray<AnnotationSetItem>(0);
            fieldAnnotationsMap = new SparseArray<AnnotationSetItem>(0);
            parameterAnnotationsMap = new SparseArray<AnnotationSetRefList>(0);
            return;
        }

        methodAnnotationsMap = new SparseArray<AnnotationSetItem>(annotationDirectory.getMethodAnnotationCount());
        annotationDirectory.iterateMethodAnnotations(new AnnotationDirectoryItem.MethodAnnotationIteratorDelegate() {
            public void processMethodAnnotations(MethodIdItem method, AnnotationSetItem methodAnnotations) {
                methodAnnotationsMap.put(method.getIndex(), methodAnnotations);
            }
        });

        fieldAnnotationsMap = new SparseArray<AnnotationSetItem>(annotationDirectory.getFieldAnnotationCount());
        annotationDirectory.iterateFieldAnnotations(new AnnotationDirectoryItem.FieldAnnotationIteratorDelegate() {
            public void processFieldAnnotations(FieldIdItem field, AnnotationSetItem fieldAnnotations) {
                fieldAnnotationsMap.put(field.getIndex(), fieldAnnotations);
            }
        });

        parameterAnnotationsMap = new SparseArray<AnnotationSetRefList>(
                annotationDirectory.getParameterAnnotationCount());
        annotationDirectory.iterateParameterAnnotations(
                new AnnotationDirectoryItem.ParameterAnnotationIteratorDelegate() {
                    public void processParameterAnnotations(MethodIdItem method, AnnotationSetRefList parameterAnnotations) {
                        parameterAnnotationsMap.put(method.getIndex(), parameterAnnotations);
                    }
                });
    }

    private void findStaticFieldInitializers() {
        // Get initializers from class data
        EncodedArrayItem encodedArrayItem = classDefItem.getStaticFieldInitializers();
        if (encodedArrayItem != null) {
            EncodedValue[] staticInitializers = encodedArrayItem.getEncodedArray().values;
            ClassDataItem.EncodedField[] staticFields = classDataItem.getStaticFields();

            for (int i = 0, staticFieldsLength = staticFields.length, staticInitializersLength = staticInitializers.length;
                 i < staticFieldsLength && i < staticInitializersLength; i++) {
                String staticField = staticFields[i].field.getFieldName().getStringValue();
                String staticInitializer = EncodedValueAdaptor.get(staticInitializers[i]);
                staticFieldInitialValues.put(staticField, staticInitializer);
            }
        }

        // Get initializers from class constructor
        if (classDataItem == null) {
            return;
        }

        MemoryWriter classInit = new MemoryWriter();
        for (ClassDataItem.EncodedMethod encodedMethod : classDataItem.getDirectMethods()) {
            if (encodedMethod.method.getMethodName().getStringValue().equals("<clinit>")) {
                try {
                    writeMethods(new IndentingWriter(classInit), new ClassDataItem.EncodedMethod[]{encodedMethod}, null);
                } catch (IOException ignore) {
                }
                break;
            }
        }
        String contents = classInit.getContents();

        if (isEnum) {
            // Find enum constructor args
            Matcher matcher = ENUM_VALUE_MATCHER.matcher(contents);
            while (matcher.find()) {
                String constructorArgs = matcher.group(2);
                if (constructorArgs != null) {
                    constructorArgs = constructorArgs.substring(2);
                }
                staticFieldInitialValues.put(matcher.group(1), constructorArgs);
            }
        } else {
            // Find static field initializers and static block contents
            int length = contents.length();
            int pos = 0;
            Matcher fieldValues = STATIC_FIELD_INITAL_VALUE.matcher(contents);
            Matcher blockContents = STATIC_BLOCK_CONTENTS.matcher(contents);
            while (pos < length) {
                if (fieldValues.find(pos)) {
                    staticFieldInitialValues.put(fieldValues.group(2), fieldValues.group(3));
                    pos = fieldValues.end();
                } else if (blockContents.find(pos)) {
                    String line = blockContents.group(1);
                    if (line == null) {
                        continue;
                    }
                    line = line.trim();
                    if (line.equals("return;")) {
                        break;
                    }
                    staticBlock.add(line);
                    pos = blockContents.end();
                }
            }
        }
    }

    public static void addImport(String newImport) {
        newImport = newImport.replace("[]", "");
        if (newImport.indexOf('.') >= 0 &&
                !(newImport.startsWith("dalvik") || newImport.startsWith(javaClassName))) {
            imports.add(newImport);
        }
    }

    public static void addImport(HashSet<String> newImports) {
        imports.addAll(newImports);
    }

    public static boolean isSuper(String dalvikClassDescription) {
        return superClass.equals(dalvikClassDescription);
    }

    public static boolean isCurrentClass(String dalvikClassDescription) {
        return dalvikClassName.equals(dalvikClassDescription);
    }

    public static boolean isAnonymous() {
        return isAnonymous;
    }

    public static boolean isInterface() {
        return isInterface;
    }

    public static boolean isEnum() {
        return isEnum;
    }

    public static String getDalvikClassName() {
        return dalvikClassName;
    }

    public static String getName() {
        return javaClassName;
    }

    public static HashMap<String, InnerClass> getInnerClasses() {
        return innerClasses;
    }

    private void parseClassDetails() {
        setClassName();
        setSuper();
        setInterfaces();
        isInterface = AccessFlags.hasFlag(classDefItem.getAccessFlags(), AccessFlags.INTERFACE);
        isEnum = AccessFlags.hasFlag(classDefItem.getAccessFlags(), AccessFlags.ENUM);

        isInnerClass = false;
        innerClassAccessFlags = 0;
        isAnonymous = false;

        AnnotationItem[] annotations = getClassAnnotations();
        for (AnnotationItem annotation : annotations) {
            AnnotationEncodedSubValue encodedAnnotation = annotation.getEncodedAnnotation();
            if (encodedAnnotation.annotationType.getTypeDescriptor().equals("Ldalvik/annotation/InnerClass;")) {
                isInnerClass = true;
                innerClassAccessFlags = ((IntEncodedValue) encodedAnnotation.values[0]).value;
                if (encodedAnnotation.values[1].getValueType().equals(ValueType.VALUE_NULL)) {
                    isAnonymous = true;
                }
            }
        }
    }

    private AnnotationItem[] getClassAnnotations() {
        AnnotationDirectoryItem annotationDirectoryItem = classDefItem.getAnnotations();
        if (annotationDirectoryItem == null) {
            return new AnnotationItem[0];
        }

        AnnotationSetItem classAnnotations = annotationDirectoryItem.getClassAnnotations();
        if (classAnnotations == null) {
            return new AnnotationItem[0];
        }

        return classAnnotations.getAnnotations();
    }

    private void setClassName() {
        dalvikClassName = classDefItem.getClassType().getTypeDescriptor();
        javaClassName = TypeFormatter.getFullType(classDefItem.getClassType());
    }

    private void setSuper() {
        TypeIdItem superClass = classDefItem.getSuperclass();
        if (superClass != null) {
            ClassDefinition.superClass = superClass.getTypeDescriptor();
        }
    }

    private void setInterfaces() {
        this.interfaces = null;
        TypeListItem interfaceList = classDefItem.getInterfaces();
        if (interfaceList == null) {
            return;
        }

        List<TypeIdItem> interfaces = interfaceList.getTypes();
        if (interfaces == null || interfaces.size() == 0) {
            return;
        }
        this.interfaces = interfaces;
    }

    /* The majority of the file is written to memory first because in order to
       know and write the imports we have to process the entire file. Thus, the
       body of the file is only written at the end.
     */
    public boolean writeTo(IndentingWriter writer) throws IOException {
        imports = new HashSet<String>();

        MemoryWriter body = new MemoryWriter();
        writeBody(new IndentingWriter(body));
        if (isInnerClass) {
            innerClasses.put(dalvikClassName, makeInnerClass(body));
            return false;
        } else {
            writeBase(writer);
            writer.write(body.getContents());
            return true;
        }
    }

    private InnerClass makeInnerClass(MemoryWriter body) {
        InnerClass innerClass;
        if (isAnonymous) {
            String anonBase = superClass.equals(LJAVA_LANG_OBJECT) ? interfaces.get(0).getTypeDescriptor() : superClass;
            innerClass = new InnerClass(body, anonBase, imports);
        } else {
            innerClass = new InnerClass(body, imports);
        }
        return innerClass;
    }

    private void writeBody(IndentingWriter writer) throws IOException {
        if (!isAnonymous) {
            if (!writeClass(writer)) {
                if (!isEnum) {
                    writeSuper(writer);
                }
                writeInterfaces(writer);
            }
        }
        writer.write(" {");
        writer.indent(4);
        writeAnnotations(writer);
        writeStaticFields(writer);
        writeInstanceFields(writer);
        writeConstructors(writer);
        writeDirectMethods(writer);
        writeVirtualMethods(writer);
        writeInnerClasses(writer);
        writer.deindent(4);
        writer.write("}");
    }

    private void writeBase(IndentingWriter writer) throws IOException {
        writePackage(writer);
        writeImports(writer);
    }

    private void writePackage(IndentingWriter writer) throws IOException {
        writer.write("package ");
        String descriptor = TypeFormatter.getFullType(classDefItem.getClassType());
        int lastPeriod = descriptor.lastIndexOf('.');
        writer.write(descriptor.substring(0, lastPeriod));
        writer.write(";\n\n");

    }

    private void writeImports(IndentingWriter writer) throws IOException {
        for (String anImport : imports) {
            writer.write("import ");
            writer.write(anImport);
            writer.write(";\n");
        }
        writer.write('\n');
    }

    private boolean writeClass(IndentingWriter writer) throws IOException {
        writeAccessFlags(writer);
        if (!isInterface && !isEnum) {
            writer.write("class ");
        }

        String descriptor = javaClassName;
        int lastPeriod = descriptor.lastIndexOf('.');
        int lastDollarSign = descriptor.lastIndexOf('$');
        int beginningOfName = lastPeriod > lastDollarSign ? lastPeriod : lastDollarSign;
        if (beginningOfName >= 0) {
            descriptor = descriptor.substring(beginningOfName + 1);
        }
        writer.write(descriptor);

        AnnotationDirectoryItem annotationDirectory = classDefItem.getAnnotations();
        if (annotationDirectory == null) {
            return false;
        }

        return SignatureFormatter.writeSignature(
                writer,
                annotationDirectory.getClassAnnotations(),
                SignatureFormatter.Origin.Class);
    }

    private void writeAccessFlags(IndentingWriter writer) throws IOException {
        if (isEnum) {
            writer.write("public enum ");
            return;
        }
        int classAccessFlags = classDefItem.getAccessFlags();
        if (isInnerClass) {
            classAccessFlags = innerClassAccessFlags;
        }
        for (AccessFlags accessFlag : AccessFlags.getAccessFlagsForClass(classAccessFlags)) {
            if (!accessFlag.equals(AccessFlags.ANNOTATION) &&
                    !(accessFlag.equals(AccessFlags.ABSTRACT) && isInterface)) {
                writer.write(accessFlag.toString());
                writer.write(' ');
            }
        }
    }

    private void writeSuper(IndentingWriter writer) throws IOException {
        if (superClass != null && !superClass.equals(LJAVA_LANG_OBJECT)) {
            writer.write(" extends ");
            writer.write(TypeFormatter.getType(superClass));
        }
    }

    private void writeInterfaces(IndentingWriter writer) throws IOException {
        if (interfaces == null) {
            return;
        }

        writer.write(" implements ");
        boolean firstTime = true;
        for (TypeIdItem typeIdItem : interfaces) {
            if (!firstTime) {
                writer.write(", ");
            }
            firstTime = false;
            writer.write(TypeFormatter.getType(typeIdItem));
        }
    }

    private void writeAnnotations(IndentingWriter writer) throws IOException {
        AnnotationDirectoryItem annotationDirectory = classDefItem.getAnnotations();
        if (annotationDirectory == null) {
            return;
        }

        AnnotationSetItem annotationSet = annotationDirectory.getClassAnnotations();
        if (annotationSet == null) {
            return;
        }

        AnnotationFormatter.writeTo(writer, annotationSet);
    }

    private void writeStaticFields(IndentingWriter writer) throws IOException {
        if (classDataItem == null) {
            return;
        }
        //if classDataItem is not null, then classDefItem won't be null either
        assert (classDefItem != null);

        ClassDataItem.EncodedField[] encodedFields = classDataItem.getStaticFields();
        if (encodedFields == null || encodedFields.length == 0) {
            return;
        }

        boolean first = true;
        for (ClassDataItem.EncodedField field : encodedFields) {
            //don't print synthetic fields
            if (AccessFlags.hasFlag(field.accessFlags, AccessFlags.SYNTHETIC)) {
                continue;
            }

            if (first) {
                writer.write("\n\n");
            } else if (isEnum()) {
                writer.write(",\n");
            }
            first = false;

            String initialValue = staticFieldInitialValues.get(field.field.getFieldName().getStringValue());
            AnnotationSetItem annotationSet = fieldAnnotationsMap.get(field.field.getIndex());

            FieldDefinition.writeTo(writer, field, initialValue, annotationSet);
        }
        if (isEnum) {
            writer.write(';');
        }
    }

    private void writeInstanceFields(IndentingWriter writer) throws IOException {
        if (classDataItem == null) {
            return;
        }

        ClassDataItem.EncodedField[] encodedFields = classDataItem.getInstanceFields();
        if (encodedFields == null || encodedFields.length == 0) {
            return;
        }

        boolean first = true;
        for (ClassDataItem.EncodedField field : classDataItem.getInstanceFields()) {
            if (AccessFlags.hasFlag(field.accessFlags, AccessFlags.SYNTHETIC)) {
                continue;
            }
            if (first) {
                writer.write("\n\n");
            } else {
                writer.write('\n');
            }
            first = false;

            AnnotationSetItem annotationSet = fieldAnnotationsMap.get(field.field.getIndex());

            FieldDefinition.writeTo(writer, field, null, annotationSet);
        }
    }

    private void writeConstructors(IndentingWriter writer) throws IOException {
        // Write static block
        if (!staticBlock.isEmpty()) {
            writer.write("\nstatic {\n");
            writer.indent(4);
            for (String instruction : staticBlock) {
                writer.write(instruction);
                writer.write('\n');
            }
            writer.deindent(4);
            writer.write("}\n");
        }

        // Handle enum constructor specially
        if (isEnum && classDataItem != null) {
            for (ClassDataItem.EncodedMethod encodedMethod : classDataItem.getDirectMethods()) {
                if (encodedMethod.method.getMethodName().getStringValue().equals("<init>")) {
                    MemoryWriter instanceInit = new MemoryWriter();
                    writeMethods(new IndentingWriter(instanceInit), new ClassDataItem.EncodedMethod[]{encodedMethod}, null);
                    String constructor = instanceInit.getContents();
                    writeEnumConstructor(writer, constructor);
                }
            }
        }


    }

    private void writeDirectMethods(IndentingWriter writer) throws IOException {
        if (classDataItem == null) {
            return;
        }

        ClassDataItem.EncodedMethod[] directMethods = classDataItem.getDirectMethods();

        if (directMethods == null || directMethods.length == 0) {
            return;
        }

        // Static constructors are handled separately and should not be printed
        // Enum constructors, valueOf and values are handled separately and should not be printed
        ArrayList<ClassDataItem.EncodedMethod> noConstructors = new ArrayList<ClassDataItem.EncodedMethod>();
        for (ClassDataItem.EncodedMethod directMethod : directMethods) {
            String methodName = directMethod.method.getMethodName().getStringValue();
            if (methodName.equals("<clinit>") ||
                    (isEnum &&
                            (methodName.equals("<init>") ||
                                    methodName.equals("valueOf") ||
                                    methodName.equals("values")))) {
                continue;
            }
            noConstructors.add(directMethod);
        }
        directMethods = noConstructors.toArray(new ClassDataItem.EncodedMethod[noConstructors.size()]);

        writeMethods(writer, directMethods, "direct methods");
    }

    private void writeVirtualMethods(IndentingWriter writer) throws IOException {
        if (classDataItem == null) {
            return;
        }

        ClassDataItem.EncodedMethod[] virtualMethods = classDataItem.getVirtualMethods();

        if (virtualMethods == null || virtualMethods.length == 0) {
            return;
        }

        writeMethods(writer, virtualMethods, "virtual methods");
    }

    private void writeMethods(IndentingWriter writer, ClassDataItem.EncodedMethod[] methods, String label) throws IOException {
        boolean first = true;
        for (ClassDataItem.EncodedMethod method : methods) {
            //don't print synthetic methods or anonymous constructors
            if (AccessFlags.hasFlag(method.accessFlags, AccessFlags.SYNTHETIC) ||
                    (ClassDefinition.isAnonymous() && AccessFlags.hasFlag(method.accessFlags, AccessFlags.CONSTRUCTOR))) {
                continue;
            }

            if (first) {
                if (label != null) {
                    writer.write("\n\n");
                    writer.write("// " + label + "\n");
                }
            } else {
                writer.write('\n');
            }
            first = false;

            AnnotationSetItem annotationSet = methodAnnotationsMap.get(method.method.getIndex());
            AnnotationSetRefList parameterAnnotationList = parameterAnnotationsMap.get(method.method.getIndex());

            MethodDefinition methodDefinition = new MethodDefinition(method);
            methodDefinition.writeTo(writer, annotationSet, parameterAnnotationList);

            ValidationException validationException = methodDefinition.getValidationException();
            if (validationException != null) {
                System.err.println(String.format("Error while disassembling method %s. Continuing.",
                        method.method.getMethodString()));
                validationException.printStackTrace(System.err);
                this.validationErrors = true;
            }
        }
    }

    private void writeInnerClasses(IndentingWriter writer) throws IOException {
        for (AnnotationItem annotation : getClassAnnotations()) {
            AnnotationEncodedSubValue encodedAnnotation = annotation.getEncodedAnnotation();

            if (encodedAnnotation.annotationType.getTypeDescriptor().equals("Ldalvik/annotation/MemberClasses;")) {
                EncodedValue[] innerClassList = ((ArrayEncodedSubValue) encodedAnnotation.values[0]).values;
                for (EncodedValue innerClass : innerClassList) {
                    String innerClassName = ((TypeEncodedValue) innerClass).value.getTypeDescriptor();
                    if (innerClasses.containsKey(innerClassName)) {
                        InnerClass namedInnerClass = innerClasses.remove(innerClassName);
                        writer.write('\n');
                        writer.write(namedInnerClass.getBody());
                        writer.write('\n');

                        imports.addAll(namedInnerClass.getImports());
                    }
                }
            }
        }
    }

    private void writeEnumConstructor(IndentingWriter writer, String constructor) throws IOException {
        if (!ENUM_IS_DEFAULT_CONSTRUCTOR.matcher(constructor).find()) {
            Matcher matcher = ENUM_CONSTRUCTOR_DECLARATION.matcher(constructor);
            if (matcher.find()) {
                writer.write(matcher.group(1));
                writer.write("(");
                String parameters = matcher.group(2);
                if (parameters != null) {
                    writer.write(parameters.substring(2));
                }
                writer.write(") {\n");
                int secondNewLine = constructor.indexOf('\n', constructor.indexOf('\n') + 1);
                writer.write(constructor.substring(secondNewLine + 1));
            }
        }
    }
}
