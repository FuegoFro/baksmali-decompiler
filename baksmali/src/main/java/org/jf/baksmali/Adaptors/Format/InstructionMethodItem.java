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

package org.jf.baksmali.Adaptors.Format;

import org.jf.baksmali.Adaptors.*;
import org.jf.baksmali.InnerClass;
import org.jf.baksmali.Parenthesizer;
import org.jf.dexlib.Code.*;
import org.jf.dexlib.*;
import org.jf.util.IndentingWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jf.dexlib.Code.Analysis.SyntheticAccessorResolver.*;

public class InstructionMethodItem<T extends Instruction> extends MethodItem {
    public static final String NUMBER = "I";
    public static final String BOOLEAN = "Z";
    public static final String OBJECT = "L;";
    private static final Pattern ANONYMOUS_CLASS = Pattern.compile("\\$\\d+;$");
    private static final Pattern THIS = Pattern.compile("([A-Za-z]\\.)?this");
    private static final Pattern NEW_ARRAY = Pattern.compile("^new [^ ]+\\[([0-9]+)\\]$");
    private static final Pattern LITERAL_ARRAY = Pattern.compile("^new [^ ]+\\[\\] \\{(.+)\\}$");
    protected static CodeItem codeItem;
    protected final T instruction;

    private static int returnedReg;
    private static String returnLabel;

    public InstructionMethodItem(CodeItem codeItem, int codeAddress, T instruction) {
        super(codeAddress);
        InstructionMethodItem.codeItem = codeItem;
        this.instruction = instruction;
        returnedReg = -1;
        returnLabel = null;
    }

    public double getSortOrder() {
        //instructions should appear after everything except an "end try" label and .catch directive
        return 100;
    }

    public short getValue() {
        return instruction.opcode.value;
    }

    public static CodeItem getCodeItem() {
        return codeItem;
    }

    public boolean keepPreviousAssignment() throws IOException {
        short value = getValue();
        // Keep if is a cast or a goto
        return value == 0x1f || (0x28 <= value && value <= 0x2a);
    }

    @Override
    public boolean writeTo(IndentingWriter writer) throws IOException {
        short value = getValue();

        if (value >= 0x01 && value <= 0x09) { //moves
            return setFirstRegisterContents(getSecondRegisterContents(), RegisterFormatter.getRegisterType(getSecondRegister()));
        } else if (value >= 0x0a && value <= 0x0c) { //move-result
            if (previousMethodCall != null) {
                String previousMethodCall = InstructionMethodItem.previousMethodCall;
                String previousMethodCallReturnType = InstructionMethodItem.previousMethodCallReturnType;
                InstructionMethodItem.previousMethodCall = null;
                InstructionMethodItem.previousMethodCallReturnType = null;
                return setFirstRegisterContents(previousMethodCall, previousMethodCallReturnType);
            } else {
                RegisterFormatter.clearRegisterContents(getFirstRegister());
                writeOpcode(writer);
                writer.write(' ');
                writeFirstRegister(writer);
                return true;
            }
        } else if (value == 0x0d) { // move-exception
            return setFirstRegisterContents("0", "Ljava/lang/Exception;");
            //Todo: This is a temporary solution to produce 'valid' java code.
            //      The local variable actually stores the caught exception rather than null.
            //      This might have to wait for actual try/catch to be done properly
        } else if (value == 0x0e) { // return-void
            writeOpcode(writer);
            writer.write(";\n\n");
            if (LabelMethodItem.lastLabelAddress == codeAddress) {
                returnLabel = LabelMethodItem.lastLabel;
            }
            returnedReg = -1;
            return false;
        } else if (value >= 0xf && value <= 0x011) { //return value
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer, MethodDefinition.getDalvikReturnType());
            writer.write(";\n\n");
            if (LabelMethodItem.lastLabelAddress == codeAddress) {
                returnedReg = getFirstRegister();
                returnLabel = LabelMethodItem.lastLabel;
            }
            return false;
        } else if (value >= 0x012 && value <= 0x019) { //const primitive
            String literal = getLiteral();
            String type = NUMBER;
            if (literal.equals("0")) {
                type = null;
            }
            if (instruction.opcode.setsWideRegister()) {
                literal += "L";
            }
            return setFirstRegisterContents(literal, type);
        } else if (value >= 0x01a && value <= 0x01c) { //const string, class
            return setFirstRegisterContents(getReference(false), getReferenceType());
        } else if (value >= 0x01d && value <= 0x01e) { //monitor
            // Todo: Skipped monitor opcodes
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            return true;
        } else if (value == 0x01f) { //check-cast
            String contents = getFirstRegisterContents();
            if (previousNonPrintingAssignment != null) {
                if (getFirstRegister() == previousNonPrintingAssignedRegister) {
                    contents = previousNonPrintingAssignment;
                } else {
                    writePreviousNonPrintingAssignment(writer);
                }
            }
            contents = Parenthesizer.ensureNoUnenclosedSpaces(contents);
            return setFirstRegisterContents("(" + getReference(false) + ") " + contents, getReferenceType());
        } else if (value == 0x020) { // instanceof
            String contents = Parenthesizer.ensureOrderOfOperations(getOpcode(), getSecondRegisterContents(), getReference(false));
            return setFirstRegisterContents(contents, BOOLEAN);
        } else if (value == 0x021) { //array-length
            String contents = Parenthesizer.ensureNoUnenclosedSpaces(getSecondRegisterContents());
            return setFirstRegisterContents(contents + getOpcode(), NUMBER);
        } else if (value == 0x022) { // new instance
            RegisterFormatter.clearRegisterContents(getFirstRegister());
            return false;
        } else if (value == 0x023) { // new-array
            String typeReference = getReference(false);
            String contents = "new " + typeReference.substring(0, typeReference.length() - 2) + "[" + getSecondRegisterContents() + "]";
            return setFirstRegisterContents(contents, getReferenceType());
        } else if (value == 0x024) { // filled-new-array
            // Todo: Skipped filled-new-array opcodes
            int regCount = ((InvokeInstruction) instruction).getRegCount();
            List<TypeIdItem> parameterTypes = new ArrayList<TypeIdItem>(regCount);
            for (int i = 0; i < regCount; i++) {
                parameterTypes.add(null);
            }
            writeOpcode(writer);
            writer.write(' ');
            writer.write(getInvocation(parameterTypes, false));
            writer.write(", ");
            writeReference(writer, false);
            return true;
        } else if (value == 0x025) { // filled-new-array-range
            // Todo: Skipped filled-new-array-range opcodes
            int regCount = ((InvokeInstruction) instruction).getRegCount();
            List<TypeIdItem> parameterTypes = new ArrayList<TypeIdItem>(regCount);
            for (int i = 0; i < regCount; i++) {
                parameterTypes.add(null);
            }
            writeOpcode(writer);
            writer.write(' ');
            writer.write(getInvocation(parameterTypes, false));
            writer.write(", ");
            writeReference(writer, false);
            return true;
        } else if (value == 0x026) { // fill-array-data
            // Todo: Skipped fill array data opcodes
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            writer.write(", ");
            writeTargetLabel(writer);
            return true;
        } else if (value == 0x027) { // throw
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer, OBJECT);
            return true;
        } else if (value >= 0x028 && value <= 0x02a) { //goto
            String label = getTargetLabel();
            if (label.equals(returnLabel)) {
                writer.write("return");
                if (returnedReg >= 0) {
                    writer.write(' ');
                    writer.write(RegisterFormatter.getRegisterContents(returnedReg, codeItem, MethodDefinition.getDalvikReturnType()));
                }
                writer.write(";\n\n");
            } else {
                if (previousNonPrintingAssignment != null) {
                    writePreviousNonPrintingAssignment(writer);
                }
                writer.write("//");
                writeOpcode(writer);
                writer.write(' ');
                writeTargetLabel(writer);
                writer.write("\n\n");
            }
            return false;
        } else if (value >= 0x02b && value <= 0x02c) {
            // Todo: Skipped packed and sparse switch opcodes
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            writer.write(", ");
            writeTargetLabel(writer);
            return true;
        } else if (value >= 0x02d && value <= 0x031) { // compare
            String compareResult = Parenthesizer.ensureOrderOfOperations("-", getSecondRegisterContents(), getThirdRegisterContents());
            return setFirstRegisterContents(compareResult, NUMBER);
        } else if (value >= 0x032 && value <= 0x037) { //if compare to reg
            writer.write("if (");
            writer.write(Parenthesizer.ensureOrderOfOperations(getOpcode(), getFirstRegisterContents(), getSecondRegisterContents()));
            writer.write(") {\n");
            writer.indent(4);
            writer.write("//goto ");
            writeTargetLabel(writer);
            writer.write('\n');
            writer.deindent(4);
            writer.write("}\n");
            return false;
        } else if (value >= 0x038 && value <= 0x03d) { //if compare to zero
            writer.write("if (");
            String zeroValue = TypeFormatter.zeroAs(RegisterFormatter.getRegisterType(getFirstRegister()));
            writer.write(Parenthesizer.ensureOrderOfOperations(getOpcode(), getFirstRegisterContents(), zeroValue));
            writer.write(") {\n");
            writer.indent(4);
            writer.write("//goto ");
            writeTargetLabel(writer);
            writer.write('\n');
            writer.deindent(4);
            writer.write("}\n");
            return false;
        } else if (value >= 0x044 && value <= 0x04a) { //aget
            return setFirstRegisterContents(getSecondRegisterContents() + "[" + getThirdRegisterContents() + "]", getArrayElementType());
        } else if (value >= 0x04b && value <= 0x051) { //aput
            String array = getSecondRegisterContents();
            Matcher newArrayMatcher = NEW_ARRAY.matcher(array);
            if (newArrayMatcher.find()) {
                try {
                    String arraySize = newArrayMatcher.group(1);
                    StringBuilder newArray = new StringBuilder();
                    newArray.append(array.substring(0, array.length() - (arraySize.length() + 1)));
                    newArray.append("] {");
                    int size = Integer.parseInt(arraySize);
                    for (int i = 0; i < size; i++) {
                        if (i != 0) {
                            newArray.append(", ");
                        }
                        newArray.append("null");
                    }
                    newArray.append("}");
                    array = newArray.toString();
                } catch (NumberFormatException ignored) {
                }
            }
            Matcher literalArrayMatcher = LITERAL_ARRAY.matcher(array);
            if (literalArrayMatcher.find()) {
                try {
                    String literalValues = literalArrayMatcher.group(1);
                    StringBuilder newArray = new StringBuilder();
                    newArray.append(array.substring(0, array.length() - (literalValues.length() + 1)));
                    String[] literalValuesArray = literalValues.split(", ");
                    int index = Integer.parseInt(getThirdRegisterContents());
                    literalValuesArray[index] = getFirstRegisterContents(getArrayElementType());
                    for (int i = 0, literalValuesArrayLength = literalValuesArray.length; i < literalValuesArrayLength; i++) {
                        if (i != 0) {
                            newArray.append(", ");
                        }
                        newArray.append(literalValuesArray[i]);
                    }
                    newArray.append("}");
                    return setRegisterContents(getSecondRegister(), newArray.toString(), getArrayType());
                } catch (NumberFormatException ignored) {
                }
            }


            writeSecondRegister(writer);
            writer.write('[');
            writeThirdRegister(writer);
            writer.write(']');
            writer.write(" = ");
            writeFirstRegister(writer, getArrayElementType());
            return true;
        } else if (value >= 0x051 && value <= 0x058) { //iget
            String secondRegister = getSecondRegisterContents();
            String contents = getReference(false);
            if (!THIS.matcher(secondRegister).find() || RegisterFormatter.isLocal(contents)) {
                contents = secondRegister + "." + contents;
            }
            return setFirstRegisterContents(contents, getReferenceType());
        } else if (value >= 0x059 && value <= 0x05f) { //iput
            String secondRegisterContents = getSecondRegisterContents();
            String referencedField = getReference(false);
            if (THIS.matcher(referencedField).find()) {
                return false;
            }
            if (!THIS.matcher(secondRegisterContents).find() || RegisterFormatter.isLocal(referencedField)) {
                writeSecondRegister(writer);
                writer.write('.');
            }
            writer.write(referencedField);
            writer.write(" = ");
            writeFirstRegister(writer, getReferenceType());
            return true;
        } else if (value >= 0x060 && value <= 0x066) { //sget
            String contents = getReference(true);
            if (RegisterFormatter.isLocal(contents)) {
                contents = getFieldClass() + "." + contents;
            }
            return setFirstRegisterContents(contents, getReferenceType());
        } else if (value >= 0x067 && value <= 0x06d) { //sput
            String fieldName = getReference(true);
            if (RegisterFormatter.isLocal(fieldName)) {
                writer.write(getFieldClass() + ".");
            }
            writer.write(fieldName);
            writer.write(" = ");
            writeFirstRegister(writer, getReferenceType());
            return true;
        } else if (value == 0x06f || value == 0x071 || value == 0x075 || value == 0x077) { //invoke (range) super and static
            List<TypeIdItem> parameterTypes = getMethodParameterTypes();
            boolean isStatic = false;
            if (value == 0x06f || value == 0x075) { // invoke super
                previousMethodCall = "super.";
            } else { // invoke static
                if (isAccessor()) {
                    return handleAccessor(writer, parameterTypes);
                }
                isStatic = true;
                previousMethodCall = "";
            }
            previousMethodCall += getReference(isStatic) + getInvocation(parameterTypes, isStatic);
            previousMethodCallReturnType = getReferenceType();
            return false;
        } else if ((value >= 0x06e && value <= 0x072) || (value >= 0x074 && value <= 0x078)) { //invoke(range) non-super non-static
            return invoke();
        } else if (value >= 0x07b && value <= 0x08f) { //conversions
            String contents = Parenthesizer.ensureNoUnenclosedSpaces(getSecondRegisterContents());
            return setFirstRegisterContents(getOpcode() + contents, NUMBER);
        } else if (value >= 0x090 && value <= 0x0af) { //basic arithmetic
            return setFirstRegisterContents(Parenthesizer.ensureOrderOfOperations(getOpcode(), getSecondRegisterContents(), getThirdRegisterContents()), NUMBER);
        } else if (value >= 0x0b0 && value <= 0x0cf) { //arithmetic, store in first
            return setFirstRegisterContents(Parenthesizer.ensureOrderOfOperations(getOpcode(), getFirstRegisterContents(), getSecondRegisterContents()), NUMBER);
        } else if (value >= 0x0d0 && value <= 0x0e2) { //literal arithmetic
            return setFirstRegisterContents(Parenthesizer.ensureOrderOfOperations(getOpcode(), getSecondRegisterContents(), getLiteral()), NUMBER);
        } else {
            assert false;
            return false;
        }
    }

    private boolean invoke() {
        List<TypeIdItem> parameterTypes = getMethodParameterTypes();
        int instanceRegister = getInstanceRegister();
        String instance = RegisterFormatter.getRegisterContents(instanceRegister, codeItem);
        instance = Parenthesizer.ensureNoUnenclosedSpaces(instance);
        String invocation = getInvocation(parameterTypes, false);

        previousMethodCallReturnType = getReferenceType();
        if (isConstructor()) {
            if (ClassDefinition.isSuper(getCalledMethodContainingClass())) {
                if (!invocation.equals("()")) {
                    previousMethodCall = "super" + invocation;
                }
            } else if (instance.equals("this")) {
                previousMethodCall = "this" + invocation;
            } else {
                previousMethodCallReturnType = null;
                String dalvikClassName = getReference(false);
                String contents = getAnonymousMethod(dalvikClassName);
                if (contents == null) {
                    contents = "new " + TypeFormatter.getType(dalvikClassName) + invocation;
                }
                return setRegisterContents(instanceRegister, contents, getReferenceType());
            }
        } else {
            previousMethodCall = getReference(false) + invocation;
            if (!THIS.matcher(instance).find()) {
                previousMethodCall = instance + "." + previousMethodCall;
            }
        }
        return false;
    }

    private String getAnonymousMethod(String dalvikClassName) {
        if (ANONYMOUS_CLASS.matcher(dalvikClassName).find()) {
            HashMap<String, InnerClass> innerClasses = ClassDefinition.getInnerClasses();
            InnerClass innerClass = innerClasses.get(dalvikClassName);

            if (innerClass != null && innerClass.isAnonymous()) {
                innerClasses.remove(dalvikClassName);
                ClassDefinition.addImport(innerClass.getImports());

                String prettyBaseName = TypeFormatter.getType(innerClass.getSuperClass());
                return "new " + prettyBaseName + "() " + innerClass.getBody();
            }
        }
        return null;
    }

    private boolean isConstructor() {
        String methodName = ((MethodIdItem) (((InstructionWithReference) instruction).getReferencedItem())).getMethodName().getStringValue();
        return methodName.equals("<init>");
    }

    protected void writeOpcode(IndentingWriter writer) throws IOException {
        writer.write(getOpcode());
    }

    protected void writeTargetLabel(IndentingWriter writer) throws IOException {
        writer.write(getTargetLabel());
    }

    protected String getTargetLabel() throws IOException {
        //this method is overrided by OffsetInstructionMethodItem, and should only be called for the formats that
        //have a target
        throw new RuntimeException();
    }

    protected void writeRegister(IndentingWriter writer, int registerNumber) throws IOException {
        RegisterFormatter.writeTo(writer, codeItem, registerNumber);
    }

    protected void writeFirstRegister(IndentingWriter writer) throws IOException {
        writer.write(getFirstRegisterContents());
    }

    protected void writeFirstRegister(IndentingWriter writer, String dalvikType) throws IOException {
        writer.write(RegisterFormatter.getRegisterContents(((SingleRegisterInstruction) instruction).getRegisterA(), codeItem, dalvikType));
    }

    protected void writeSecondRegister(IndentingWriter writer) throws IOException {
        writer.write(getSecondRegisterContents());
    }

    protected void writeThirdRegister(IndentingWriter writer) throws IOException {
        writer.write(getThirdRegisterContents());
    }

    protected void writeReference(IndentingWriter writer, boolean isStatic) throws IOException {
        writer.write(getReference(isStatic));
    }

    private boolean setRegisterContents(int register, String contents, String type) {
        RegisterFormatter.setRegisterContents(register, contents, type);
        previousNonPrintingAssignment = contents;
        previousNonPrintingAssignedRegister = register;
        return false;
    }

    private boolean setFirstRegisterContents(String contents, String type) {
        return setRegisterContents(getFirstRegister(), contents, type);
    }

    private int getFirstRegister() {
        return ((SingleRegisterInstruction) instruction).getRegisterA();
    }

    private int getSecondRegister() {
        return ((TwoRegisterInstruction) instruction).getRegisterB();
    }

    private String getFirstRegisterContents() {
        return RegisterFormatter.getRegisterContents(((SingleRegisterInstruction) instruction).getRegisterA(), codeItem);
    }

    private String getFirstRegisterContents(String registerType) {
        return RegisterFormatter.getRegisterContents(((SingleRegisterInstruction) instruction).getRegisterA(), codeItem, registerType);
    }

    private String getSecondRegisterContents() {
        return RegisterFormatter.getRegisterContents(((TwoRegisterInstruction) instruction).getRegisterB(), codeItem);
    }

    private String getThirdRegisterContents() {
        return RegisterFormatter.getRegisterContents(((ThreeRegisterInstruction) instruction).getRegisterC(), codeItem);
    }

    private String getLiteral() {
        return String.valueOf(((LiteralInstruction) instruction).getLiteral());
    }

    private String getOpcode() {
        return instruction.opcode.name;
    }

    private String getReference(boolean isStatic) {
        return ReferenceFormatter.getReference(((InstructionWithReference) instruction).getReferencedItem(), isStatic);
    }

    private String getReferenceType() {
        Item item = ((InstructionWithReference) instruction).getReferencedItem();
        return ReferenceFormatter.getReferenceType(item);
    }

    private String getCalledMethodContainingClass() {
        MethodIdItem item = (MethodIdItem) ((InstructionWithReference) instruction).getReferencedItem();
        return item.getContainingClass().getTypeDescriptor();
    }

    protected String getInvocation(List<TypeIdItem> parameterTypes, boolean isStatic) {
        InvokeInstruction instruction = (InvokeInstruction) this.instruction;
        int parameterSize = parameterTypes.size();
        int staticOffset = isStatic ? 0 : 1;

        StringBuilder invocation = new StringBuilder("(");
        boolean first = true;
        for (int i = 0; i < parameterSize; i++) {
            if (!first) {
                invocation.append(", ");
            }
            first = false;

            String dalvikType = null;
            TypeIdItem typeIdItem = parameterTypes.get(i);
            if (typeIdItem != null) {
                dalvikType = typeIdItem.toShorty();
            }
            String registerContents = getRegisterFromInstruction(instruction, i + staticOffset, dalvikType);
            invocation.append(registerContents);
        }
        invocation.append(")");

        return invocation.toString();
    }

    private int getInstanceRegister() {
        if (instruction instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction fiveReg = (FiveRegisterInstruction) instruction;
            return fiveReg.getRegisterD();
        } else if (instruction instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction rangeReg = (RegisterRangeInstruction) instruction;
            return rangeReg.getStartRegister();
        } else {
            throw new RuntimeException("Method must be called with either a FiveRegisterInstruction or RangeRegisterInstruction");
        }
    }

    private List<TypeIdItem> getMethodParameterTypes() {
        MethodIdItem item = (MethodIdItem) ((InstructionWithReference) instruction).getReferencedItem();
        List<TypeIdItem> parameterTypes = item.getPrototype().getParameterTypes();

        if (parameterTypes.size() > 0) {
            String lastParameter = parameterTypes.get(parameterTypes.size() - 1).getTypeDescriptor();
            if (item.getMethodName().getStringValue().equals("<init>") &&
                    ANONYMOUS_CLASS.matcher(lastParameter).find()) {
                // Remove the last parameter to call the non-synthetic constructor
                ArrayList<TypeIdItem> nonSyntheticConstructor = new ArrayList<TypeIdItem>();
                for (int i = 0; i < parameterTypes.size() - 1; i++) {
                    nonSyntheticConstructor.add(parameterTypes.get(i));
                }
                parameterTypes = nonSyntheticConstructor;
            }
        }
        return parameterTypes;
    }

    private String getArrayType() {
        return RegisterFormatter.getRegisterType(getSecondRegister());
    }

    private String getArrayElementType() {
        String arrayType = getArrayType();
        if (arrayType == null) {
//            System.err.println("NULL ARRAY. In method " + MethodDefinition.getName() + " in class " + ClassDefinition.getName());
            return null;
        } else if (arrayType.length() < 2 || arrayType.charAt(0) != '[') {
//            System.err.println("Non array type parsed as array: " + arrayType + " In method " + MethodDefinition.getName() + " in class " + ClassDefinition.getName());
            return null;
        } // Todo: Check why non-array types are being passed to this
        return arrayType.substring(1);
    }

    private String getRegisterFromInstruction(InvokeInstruction instruction, int register, String dalvikType) {
        int registerNumber;

        if (instruction instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction fiveReg = (FiveRegisterInstruction) instruction;
            switch (register) {
                case 0:
                    registerNumber = fiveReg.getRegisterD();
                    break;
                case 1:
                    registerNumber = fiveReg.getRegisterE();
                    break;
                case 2:
                    registerNumber = fiveReg.getRegisterF();
                    break;
                case 3:
                    registerNumber = fiveReg.getRegisterG();
                    break;
                case 4:
                    registerNumber = fiveReg.getRegisterA();
                    break;
                default:
                    System.err.println("Register number must be a value between 0 and 4");
                    return "";
            }
        } else if (instruction instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction rangeReg = (RegisterRangeInstruction) instruction;
            registerNumber = rangeReg.getStartRegister() + register;
        } else {
            throw new RuntimeException("Method must be called with either a FiveRegisterInstruction or RangeRegisterInstruction");
        }
        return RegisterFormatter.getRegisterContents(registerNumber, codeItem, dalvikType);
    }

    private String getFieldClass() {
        FieldIdItem item = (FieldIdItem) ((InstructionWithReference) instruction).getReferencedItem();
        return TypeFormatter.getType(item.getContainingClass());
    }

    private boolean isAccessor() {
        return getAccessedMember() != null;
    }

    private AccessedMember getAccessedMember() {
        return ((MethodIdItem) ((InstructionWithReference) instruction).getReferencedItem()).getAccessedMember();
    }

    private boolean handleAccessor(IndentingWriter writer, List<TypeIdItem> parameterTypes) throws IOException {
        AccessedMember member = getAccessedMember();
        int accessorType = member.getAccessedMemberType();
        String firstReg = getRegisterFromInstruction((InvokeInstruction) instruction, 0, NUMBER);
        String memberName = ReferenceFormatter.getReference(member.getAccessedMember(), false);
        String memberType = ReferenceFormatter.getReferenceType(member.getAccessedMember());

        if (THIS.matcher(firstReg).find()) {
            firstReg = "";
        } else {
            firstReg += ".";
        }

        String secondReg = null;
        if (accessorType == SETTER || accessorType == INCREMENTER_BY_VALUE) {
            secondReg = getRegisterFromInstruction((InvokeInstruction) instruction, 1, memberType);
        }

        switch (accessorType) {
            case GETTER:
//              Getter: Instance: first arg, Field: member -> PrevMethod = first.member
                previousMethodCall = firstReg + memberName;
                previousMethodCallReturnType = memberType;
                return false;
            case SETTER:
//              Setter: Instance: first arg, Field: member, Value: second arg -> print: first.member = second, PrevMethod(Type) = null
                writer.write(firstReg);
                writer.write(memberName);
                writer.write(" = ");
                writer.write(secondReg);
                return true;
            case METHOD:
//              Calls: Instance: first arg, Method: member, Args, rest of args -> PrevMethod = first.member(rest)
                // Remove the first parameter so that the registers agree with parameters to look non-static
                ArrayList<TypeIdItem> shortenedParameterTypes = new ArrayList<TypeIdItem>();
                boolean firstParameterType = true;
                for (TypeIdItem parameterType : parameterTypes) {
                    if (firstParameterType) {
                        firstParameterType = false;
                        continue;
                    }
                    shortenedParameterTypes.add(parameterType);
                }
                previousMethodCall = firstReg + memberName + getInvocation(shortenedParameterTypes, false);
                previousMethodCallReturnType = memberType;
                return false;
            case INCREMENTER_BY_VALUE:
//              +=: Instance: first arg, Field: member, Value: second arg -> first.member += second
                writer.write(firstReg);
                writer.write(memberName);
                writer.write(" += ");
                writer.write(secondReg);
                return true;
            case INCREMENTER_BY_ONE:
//              ++: Instance: first arg, Field: member -> PrevMethod = first.member++
                previousMethodCall = firstReg + memberName + "++";
                previousMethodCallReturnType = memberType;
                return false;
            case DECREMENTER_BY_ONE:
//              --: Instance: first arg, Field: member -> PrevMethod = first.member--
                previousMethodCall = firstReg + memberName + "--";
                previousMethodCallReturnType = memberType;
                return false;
        }
        return false;
    }
}
