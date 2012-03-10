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
import org.jf.dexlib.Code.*;
import org.jf.dexlib.CodeItem;
import org.jf.dexlib.Item;
import org.jf.dexlib.MethodIdItem;
import org.jf.dexlib.TypeIdItem;
import org.jf.dexlib.Util.AccessFlags;
import org.jf.util.IndentingWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InstructionMethodItem<T extends Instruction> extends MethodItem {
    public static final String NUMBER = "I";
    public static final String BOOLEAN = "Z";
    public static final String OBJECT = "L;";
    protected final CodeItem codeItem;
    protected final T instruction;

    private static String previousMethodCall = null;
    private static String previousMethodCallReturnType = null;
    private static String previousNonPrintingInstruction = null;


    public InstructionMethodItem(CodeItem codeItem, int codeAddress, T instruction) {
        super(codeAddress);
        this.codeItem = codeItem;
        this.instruction = instruction;
    }

    public double getSortOrder() {
        //instructions should appear after everything except an "end try" label and .catch directive
        return 100;
    }

    @Override
    public boolean writeTo(IndentingWriter writer) throws IOException {
        short value = instruction.opcode.value;
        if (previousMethodCall != null && !(value >= 0x0a && value <= 0x0d)) {
            writer.write(previousMethodCall);
            writer.write(";\n");
            previousMethodCall = null;
            previousMethodCallReturnType = null;
        }
        if (previousNonPrintingInstruction != null && !(value >= 0x028 && value <= 0x02a)) {
            previousNonPrintingInstruction = null;
        }

        if (value >= 0x01 && value <= 0x09) { //moves
            return setFirstRegisterContents(writer, getSecondRegisterContents(), RegisterFormatter.getRegisterType(getSecondRegister()));
        } else if (value >= 0x0a && value <= 0x0c) { //move-result
            if (previousMethodCall != null) {
                String previousMethodCall = InstructionMethodItem.previousMethodCall;
                String previousMethodCallReturnType = InstructionMethodItem.previousMethodCallReturnType;
                InstructionMethodItem.previousMethodCall = null;
                InstructionMethodItem.previousMethodCallReturnType = null;
                return setFirstRegisterContents(writer, previousMethodCall, previousMethodCallReturnType);
            } else {
                RegisterFormatter.clearRegisterContents(getFirstRegister());
                writeOpcode(writer);
                writer.write(' ');
                writeFirstRegister(writer);
                return true;
            }
        } else if (value == 0x0d) { // move-exception
            return setFirstRegisterContents(writer, "caught-exception", "Ljava/lang/Exception;");
            //Todo: How to handle this properly? Might have to wait for proper try/catch
        } else if (value == 0x0e) { // return-void
            writeOpcode(writer);
            return true;
        } else if (value >= 0xf && value <= 0x011) { //return value
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer, MethodDefinition.getDalvikReturnType());
            int register = getFirstRegister();
            if (!RegisterFormatter.isLocal(register)) {
                writer.write("; //return ");
                writer.write(RegisterFormatter.getRegisterName(codeItem, register));
            }
            return true;
        } else if (value >= 0x012 && value <= 0x019) { //const primitive
            String literal = getLiteral();
            String type = NUMBER;
            if (literal.equals("0")) {
                type = null;
            }
            return setFirstRegisterContents(writer, literal, type);
        } else if (value >= 0x01a && value <= 0x01c) { //const string, class
            return setFirstRegisterContents(writer, getReference(false), getReferenceType());
        } else if (value >= 0x01d && value <= 0x01e) { //monitor
            // Todo: Skipped monitor opcodes
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            return true;
        } else if (value == 0x01f) { //check-cast
            return setFirstRegisterContents(writer, "(" + getReference(false) + ") " + getFirstRegisterContents(), getReferenceType());
        } else if (value == 0x020) { // instanceof
            return setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getReference(false), BOOLEAN);
        } else if (value == 0x021) { //array-length
            return setFirstRegisterContents(writer, getSecondRegisterContents() + getOpcode(), NUMBER);
        } else if (value == 0x022) { // new instance
            RegisterFormatter.clearRegisterContents(getFirstRegister());
            return false;
        } else if (value == 0x023) { // new-array
            String typeReference = getReference(false);
            return setFirstRegisterContents(writer, "new " + typeReference.substring(0, typeReference.length() - 2) + "[" + getSecondRegisterContents() + "]", getReferenceType());
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
            if (previousNonPrintingInstruction != null) {
                writer.write(previousNonPrintingInstruction);
                writer.write(";\n");
            }
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeTargetLabel(writer);
            writer.write("\n\n");
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
        } else if (value >= 0x02d && value <= 0x031) {
            // Todo: Skipped compares
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            writer.write(", ");
            writeSecondRegister(writer);
            writer.write(", ");
            writeThirdRegister(writer);
            return true;
        } else if (value >= 0x032 && value <= 0x037) { //if compare to reg
            writer.write("if (");
            writeFirstRegister(writer);
            writer.write(' ');
            writeOpcode(writer);
            writer.write(' ');
            writeSecondRegister(writer);
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
            writeFirstRegister(writer);
            writer.write(' ');
            writeOpcode(writer);
            String zeroValue = TypeFormatter.zeroAs(RegisterFormatter.getRegisterType(getFirstRegister()));
            writer.write(" " + zeroValue + ") {\n");
            writer.indent(4);
            writer.write("//goto ");
            writeTargetLabel(writer);
            writer.write('\n');
            writer.deindent(4);
            writer.write("}\n");
            return false;
        } else if (value >= 0x044 && value <= 0x04a) { //aget
            return setFirstRegisterContents(writer, getSecondRegisterContents() + "[" + getThirdRegisterContents() + "]", getArrayType());
        } else if (value >= 0x04b && value <= 0x051) { //aput
            writeSecondRegister(writer);
            writer.write('[');
            writeThirdRegister(writer);
            writer.write(']');
            writer.write(" = ");
            writeFirstRegister(writer, getArrayType());
            return true;
        } else if (value >= 0x051 && value <= 0x058) { //iget
            String contents = getReference(false);
            String secondRegister = getSecondRegisterContents();
            if (!secondRegister.equals("this") || RegisterFormatter.isLocal(contents)) {
                contents = getSecondRegisterContents() + "." + contents;
            }
            return setFirstRegisterContents(writer, contents, getReferenceType());
        } else if (value >= 0x059 && value <= 0x05f) { //iput
            int parameterRegisterCount = codeItem.getParent().method.getPrototype().getParameterRegisterCount()
                    + (!AccessFlags.hasFlag(codeItem.getParent().accessFlags, AccessFlags.STATIC) ? 1 : 0);
            int registerCount = codeItem.getRegisterCount();
            int secondRegister = ((TwoRegisterInstruction) instruction).getRegisterB();
            String referencedClass = getReference(false);
            if (secondRegister != registerCount - parameterRegisterCount || RegisterFormatter.isLocal(referencedClass)) {
                writeSecondRegister(writer);
                writer.write('.');
            }
            writer.write(referencedClass);
            writer.write(" = ");
            writeFirstRegister(writer, getReferenceType());
            return true;
        } else if (value >= 0x060 && value <= 0x066) { //sget
            return setFirstRegisterContents(writer, getReference(true), getReferenceType());
        } else if (value >= 0x067 && value <= 0x06d) { //sput
            writeReference(writer, true);
            writer.write(" = ");
            writeFirstRegister(writer, getReferenceType());
            return true;
        } else if (value == 0x06f || value == 0x071 || value == 0x075 || value == 0x077) { //invoke (range) super and static
            List<TypeIdItem> parameterTypes = getMethodParameterTypes();
            boolean isStatic = false;
            if (value == 0x06f || value == 0x075) { // invoke super
                previousMethodCall = "super.";
            } else { // invoke static
                isStatic = true;
                previousMethodCall = "";
            }
            previousMethodCall += getReference(isStatic) + getInvocation(parameterTypes, isStatic);
            previousMethodCallReturnType = getReferenceType();
            return false;
        } else if ((value >= 0x06e && value <= 0x072) || (value >= 0x074 && value <= 0x078)) { //invoke(range) non-super non-static
            return invoke(writer);
        } else if (value >= 0x07b && value <= 0x08f) { //conversions
            return setFirstRegisterContents(writer, getOpcode() + getSecondRegisterContents(), NUMBER);
        } else if (value >= 0x090 && value <= 0x0af) { //basic arithmetic
            return setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getThirdRegisterContents(), NUMBER);
        } else if (value >= 0x0b0 && value <= 0x0cf) { //arithmetic, store in first
            return setFirstRegisterContents(writer, getFirstRegisterContents() + " " + getOpcode() + " " + getSecondRegisterContents(), NUMBER);
        } else if (value >= 0x0d0 && value <= 0x0e2) { //literal arithmetic
            return setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getLiteral(), NUMBER);
        } else {
            assert false;
            return false;
        }
    }

    private boolean invoke(IndentingWriter writer) throws IOException {
        String instance;
        int instanceRegister;
        String invocation;
        List<TypeIdItem> parameterTypes = getMethodParameterTypes();
        instanceRegister = getInstanceRegister();
        instance = RegisterFormatter.getRegisterContents(codeItem, instanceRegister);
        invocation = getInvocation(parameterTypes, false);

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
                return setRegisterContents(writer, instanceRegister, "new " + getReference(false) + invocation, getReferenceType());
            }
        } else {
            previousMethodCall = getReference(false) + invocation;
            if (!instance.equals("this")) {
                previousMethodCall = instance + "." + previousMethodCall;
            }
        }
        return false;
    }

    private boolean isConstructor() {
        String methodName = ((MethodIdItem) (((InstructionWithReference) instruction).getReferencedItem())).getMethodName().getStringValue();
        return methodName.equals("<init>");
    }

    protected void writeOpcode(IndentingWriter writer) throws IOException {
        writer.write(getOpcode());
    }

    protected void writeTargetLabel(IndentingWriter writer) throws IOException {
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
        writer.write(RegisterFormatter.getRegisterContents(codeItem, ((SingleRegisterInstruction) instruction).getRegisterA(), dalvikType));
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

    private boolean setRegisterContents(IndentingWriter writer, int register, String contents, String type) throws IOException {
        if (RegisterFormatter.isLocal(register)) {
            writeRegister(writer, register);
            writer.write(" = ");
            writer.write(contents);
            return true;
        } else {
            previousNonPrintingInstruction = RegisterFormatter.getRegisterName(codeItem, register) + " = " + contents;
            RegisterFormatter.setRegisterContents(register, contents, type);
            return false;
        }
    }

    private boolean setFirstRegisterContents(IndentingWriter writer, String contents, String type) throws IOException {
        return setRegisterContents(writer, getFirstRegister(), contents, type);
    }

    private int getFirstRegister() {
        return ((SingleRegisterInstruction) instruction).getRegisterA();
    }

    private int getSecondRegister() {
        return ((TwoRegisterInstruction) instruction).getRegisterB();
    }

    private String getFirstRegisterContents() {
        return RegisterFormatter.getRegisterContents(codeItem, ((SingleRegisterInstruction) instruction).getRegisterA());
    }

    private String getSecondRegisterContents() {
        return RegisterFormatter.getRegisterContents(codeItem, ((TwoRegisterInstruction) instruction).getRegisterB());
    }

    private String getThirdRegisterContents() {
        return RegisterFormatter.getRegisterContents(codeItem, ((ThreeRegisterInstruction) instruction).getRegisterC());
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
        final int regCount = instruction.getRegCount();
        int parameterSize = parameterTypes.size();
        int staticOffset = isStatic ? 0 : 1;

        StringBuilder invocation = new StringBuilder("(");
        boolean first = true;
        for (int i = staticOffset; i < regCount; i++) {
            if (!first) {
                invocation.append(", ");
            }
            first = false;

            String dalvikType = null;
            if (i < parameterSize) {
                TypeIdItem typeIdItem = parameterTypes.get(i - staticOffset);
                if (typeIdItem != null) {
                    dalvikType = typeIdItem.toShorty();
                }
            }
            String registerContents = getRegisterFromInstruction(instruction, i, dalvikType);
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
        return item.getPrototype().getParameterTypes();
    }

    private String getArrayType() {
        String arrayType = RegisterFormatter.getRegisterType(getSecondRegister());
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
        return RegisterFormatter.getRegisterContents(codeItem, registerNumber, dalvikType);
    }
}
