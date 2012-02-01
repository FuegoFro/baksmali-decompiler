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
import org.jf.dexlib.Util.AccessFlags;
import org.jf.util.IndentingWriter;

import java.io.IOException;

public class InstructionMethodItem<T extends Instruction> extends MethodItem {
    protected final CodeItem codeItem;
    protected final T instruction;
    private static String previousMethodCall = null;
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
        if (previousMethodCall != null && (value < 0x0a || value > 0x0d)) {
            writer.write(previousMethodCall);
            writer.write(";\n");
            previousMethodCall = null;
        }
        if (previousNonPrintingInstruction != null && (value < 0x028 || value > 0x02a)) {
            previousNonPrintingInstruction = null;
        }

        if (value >= 0x01 && value <= 0x09) { //moves
            setFirstRegisterContents(writer, getSecondRegisterContents());
        } else if (value >= 0x0a && value <= 0x0c) { //move-result
            if (previousMethodCall != null) {
                setFirstRegisterContents(writer, previousMethodCall);
                previousMethodCall = null;
            } else {
                RegisterFormatter.clearRegisterContents(getFirstRegister());
                writeOpcode(writer);
                writer.write(' ');
                writeFirstRegister(writer);
            }
        } else if (value == 0x0d) { // move-exception
            setFirstRegisterContents(writer, "caught-exception");
            //Todo: How to handle this properly? Might have to wait for proper try/catch
        } else if (value == 0x0e) { // return-void
            writeOpcode(writer);
        } else if (value >= 0xf && value <= 0x011) { //return value
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            int register = getFirstRegister();
            if (!RegisterFormatter.isLocal(register)) {
                writer.write("; //return ");
                writer.write(RegisterFormatter.getRegisterName(codeItem, register));
            }
        } else if (value >= 0x012 && value <= 0x019) { //const primitive
            setFirstRegisterContents(writer, getLiteral());
        } else if (value >= 0x01a && value <= 0x01c) { //const string, class
            setFirstRegisterContents(writer, getReference());
        } else if (value >= 0x01d && value <= 0x01e) { //monitor
            // Todo: Skipped monitor opcodes
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
        } else if (value == 0x01f) { //check-cast
            setFirstRegisterContents(writer, "(" + getReference() + ") " + getFirstRegisterContents());
        } else if (value == 0x020) { // instanceof
            setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getReference());
        } else if (value == 0x021) { //array-length
            setFirstRegisterContents(writer, getSecondRegisterContents() + getOpcode());
        } else if (value == 0x022) { // new instance
            RegisterFormatter.clearRegisterContents(getFirstRegister());
        } else if (value == 0x023) { // new-array
            String typeReference = getReference();
            setFirstRegisterContents(writer, "new " + typeReference.substring(0, typeReference.length() - 2) + "[" + getSecondRegisterContents() + "]");
        } else if (value == 0x024) { // filled-new-array
            // Todo: Skipped filled-new-array opcodes
            writeOpcode(writer);
            writer.write(' ');
            writeInvokeRegisters(writer);
            writer.write(", ");
            writeReference(writer);
        } else if (value == 0x025) { // filled-new-array-range
            // Todo: Skipped filled-new-array-range opcodes
            writeOpcode(writer);
            writer.write(' ');
            writeInvokeRangeRegisters(writer);
            writer.write(", ");
            writeReference(writer);
        } else if (value == 0x026) { // fill-array-data
            // Todo: Skipped fill array data opcodes
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            writer.write(", ");
            writeTargetLabel(writer);
        } else if (value == 0x027) { // throw
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
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
        } else if (value >= 0x02b && value <= 0x02c) {
            // Todo: Skipped packed and sparse switch opcodes
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            writer.write(", ");
            writeTargetLabel(writer);
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
        } else if (value >= 0x038 && value <= 0x03d) { //if compare to zero
            writer.write("if (");
            writeFirstRegister(writer);
            writer.write(' ');
            writeOpcode(writer);
            writer.write(" 0) {\n");
            writer.indent(4);
            writer.write("//goto ");
            writeTargetLabel(writer);
            writer.write('\n');
            writer.deindent(4);
            writer.write("}\n");
        } else if (value >= 0x044 && value <= 0x04a) { //aget
            setFirstRegisterContents(writer, getSecondRegisterContents() + "[" + getThirdRegisterContents() + "]");
        } else if (value >= 0x04b && value <= 0x051) { //aput
            writeSecondRegister(writer);
            writer.write('[');
            writeThirdRegister(writer);
            writer.write(']');
            writer.write(" = ");
            writeFirstRegister(writer);
        } else if (value >= 0x051 && value <= 0x058) { //iget
            String contents = getReference();
            String secondRegister = getSecondRegisterContents();
            if (!secondRegister.equals("this") || RegisterFormatter.isLocal(contents)) {
                contents = getSecondRegisterContents() + "." + contents;
            }
            setFirstRegisterContents(writer, contents);
        } else if (value >= 0x059 && value <= 0x05f) { //iput
            int parameterRegisterCount = codeItem.getParent().method.getPrototype().getParameterRegisterCount()
                    + (!AccessFlags.hasFlag(codeItem.getParent().accessFlags, AccessFlags.STATIC) ? 1 : 0);
            int registerCount = codeItem.getRegisterCount();
            int secondRegister = ((TwoRegisterInstruction) instruction).getRegisterB();
            String referencedClass = getReference();
            if (secondRegister != registerCount - parameterRegisterCount || RegisterFormatter.isLocal(referencedClass)) {
                writeSecondRegister(writer);
                writer.write('.');
            }
            writer.write(referencedClass);
            writer.write(" = ");
            writeFirstRegister(writer);
        } else if (value >= 0x060 && value <= 0x066) { //sget
            setFirstRegisterContents(writer, getStaticReference());
        } else if (value >= 0x067 && value <= 0x06d) { //sput
            writeStaticReference(writer);
            writer.write(" = ");
            writeFirstRegister(writer);
        } else if (value >= 0x06e && value <= 0x072 && value != 0x06f && value != 0x071) { //invoke non-super non-static
            invoke(writer, false);
        } else if (value == 0x06f) { //invoke super
            previousMethodCall = "super." + getReference() + getInvocation();
        } else if (value == 0x071) { //invoke static
            previousMethodCall = getStaticReference() + getStaticInvocation();
        } else if (value >= 0x074 && value <= 0x078 && value != 0x075 && value != 0x077) { // invoke-range non-super non-static
           invoke(writer, true);
        } else if (value == 0x075) { //invoke-range super
            previousMethodCall = "super." + getReference() + getRangeInvocation();
        } else if (value == 0x077) { // invoke-range static
            previousMethodCall = getStaticReference() + getStaticRangeInvocation();
        } else if (value >= 0x07b && value <= 0x08f) { //conversions
            setFirstRegisterContents(writer, getOpcode() + getSecondRegisterContents());
        } else if (value >= 0x090 && value <= 0x0af) { //basic arithmetic
            setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getThirdRegisterContents());
        } else if (value >= 0x0b0 && value <= 0x0cf) { //arithmetic, store in first
            setFirstRegisterContents(writer, getFirstRegisterContents() + " " + getOpcode() + " " + getSecondRegisterContents());
        } else if (value >= 0x0d0 && value <= 0x0e2) { //literal arithmetic
            setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getLiteral());
        } else {
            assert false;
            return false;
        }
        return true;
    }

    private void invoke(IndentingWriter writer, boolean isRange) throws IOException {
        String instance;
        int instanceRegister;
        String invocation;
        if (isRange) {
            instance = getRangeInstance();
            instanceRegister = getRangeInstanceRegister();
            invocation = getRangeInvocation();
        } else {
            instance = getInstance();
            instanceRegister = getInstanceRegister();
            invocation = getInvocation();
        }

        if (isConstructor()) {
            if (ClassDefinition.isSuper(getCalledMethodContainingClass())) {
                if (!invocation.equals("()")) {
                    previousMethodCall = "super" + invocation;
                }
            } else if (instance.equals("this")) {
                previousMethodCall = "this" + invocation;
            } else {
                setRegisterContents(writer, instanceRegister, "new " + getReference() + invocation);
            }
        } else {
            previousMethodCall = getReference() + invocation;
            if (!instance.equals("this")) {
                previousMethodCall = instance + "." + previousMethodCall;
            }
        }
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
        writeRegister(writer, ((SingleRegisterInstruction) instruction).getRegisterA());
    }

    protected void writeSecondRegister(IndentingWriter writer) throws IOException {
        writeRegister(writer, ((TwoRegisterInstruction) instruction).getRegisterB());
    }

    protected void writeThirdRegister(IndentingWriter writer) throws IOException {
        writeRegister(writer, ((ThreeRegisterInstruction) instruction).getRegisterC());
    }

    protected void writeInvokeRegisters(IndentingWriter writer) throws IOException {
        FiveRegisterInstruction instruction = (FiveRegisterInstruction) this.instruction;
        final int regCount = instruction.getRegCount();

        writer.write('(');
        switch (regCount) {
            case 2:
                writeRegister(writer, instruction.getRegisterE());
                break;
            case 3:
                writeRegister(writer, instruction.getRegisterE());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterF());
                break;
            case 4:
                writeRegister(writer, instruction.getRegisterE());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterF());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterG());
                break;
            case 5:
                writeRegister(writer, instruction.getRegisterE());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterF());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterG());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterA());
                break;
        }
        writer.write(')');
    }

    protected void writeInvokeRangeRegisters(IndentingWriter writer) throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;

        int regCount = instruction.getRegCount();
        if (regCount == 0) {
            writer.write("()");
        } else {
            int startRegister = instruction.getStartRegister();
            RegisterFormatter.writeRegisterRange(writer, codeItem, startRegister + 1, startRegister + regCount - 1);
        }
    }

    protected void writeReference(IndentingWriter writer) throws IOException {
        Item item = ((InstructionWithReference) instruction).getReferencedItem();
        ReferenceFormatter.writeReference(writer, item);
    }

    private void writeStaticReference(IndentingWriter writer) throws IOException {
        Item item = ((InstructionWithReference) instruction).getReferencedItem();
        writer.write(ReferenceFormatter.getReference(item, true));
    }

    private void setRegisterContents(IndentingWriter writer, int register, String contents) throws IOException {
        if (RegisterFormatter.isLocal(register)) {
            writeRegister(writer, register);
            writer.write(" = ");
            writer.write(contents);
        } else {
            previousNonPrintingInstruction = RegisterFormatter.getRegisterName(codeItem, register) + " = " + contents;
            RegisterFormatter.setRegisterContents(register, contents);
        }
    }

    private void setFirstRegisterContents(IndentingWriter writer, String contents) throws IOException {
        setRegisterContents(writer, getFirstRegister(), contents);
    }

    private int getFirstRegister() {
        return ((SingleRegisterInstruction) instruction).getRegisterA();
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

    private String getReference() {
        return ReferenceFormatter.getReference(((InstructionWithReference) instruction).getReferencedItem(), false);
    }

    private String getCalledMethodContainingClass() {
        MethodIdItem item = (MethodIdItem) ((InstructionWithReference) instruction).getReferencedItem();
        return item.getContainingClass().getTypeDescriptor();
    }

    protected String getStaticReference() {
        return ReferenceFormatter.getReference(((InstructionWithReference) instruction).getReferencedItem(), true);
    }

    protected String getInvocation() throws IOException {
        FiveRegisterInstruction instruction = (FiveRegisterInstruction) this.instruction;
        final int regCount = instruction.getRegCount();

        String invocation = "(";
        switch (regCount) {
            case 2:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                break;
            case 3:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterF());
                break;
            case 4:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterF());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterG());
                break;
            case 5:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterF());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterG());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterA());
                break;
        }
        invocation += ")";
        return invocation;
    }

    protected String getStaticInvocation() throws IOException {
        FiveRegisterInstruction instruction = (FiveRegisterInstruction) this.instruction;
        final int regCount = instruction.getRegCount();

        String invocation = "(";
        switch (regCount) {
            case 1:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterD());
                break;
            case 2:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterD());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                break;
            case 3:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterD());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterF());
                break;
            case 4:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterD());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterF());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterG());
                break;
            case 5:
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterD());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterE());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterF());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterG());
                invocation += ", ";
                invocation += RegisterFormatter.getRegisterContents(codeItem, (int) instruction.getRegisterA());
                break;
        }
        invocation += ")";
        return invocation;
    }

    protected String getRangeInvocation() throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;

        int regCount = instruction.getRegCount();
        if (regCount == 0) {
            return "()";
        } else {
            int startRegister = instruction.getStartRegister();
            return RegisterFormatter.getRegisterRange(codeItem, startRegister + 1, startRegister + regCount - 1);
        }
    }

    protected String getStaticRangeInvocation() throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;

        int regCount = instruction.getRegCount();
        if (regCount == 0) {
            return "()";
        } else {
            int startRegister = instruction.getStartRegister();
            return RegisterFormatter.getRegisterRange(codeItem, startRegister, startRegister + regCount - 1);
        }
    }

    private int getInstanceRegister() throws IOException {
        FiveRegisterInstruction instruction = (FiveRegisterInstruction) this.instruction;
        return instruction.getRegisterD();
    }

    private String getInstance() throws IOException {
        FiveRegisterInstruction instruction = (FiveRegisterInstruction) this.instruction;
        return RegisterFormatter.getRegisterContents(codeItem, instruction.getRegisterD());
    }

    private int getRangeInstanceRegister() throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;
        return instruction.getStartRegister();
    }

    private String getRangeInstance() throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;
        return RegisterFormatter.getRegisterContents(codeItem, instruction.getStartRegister());
    }
}
