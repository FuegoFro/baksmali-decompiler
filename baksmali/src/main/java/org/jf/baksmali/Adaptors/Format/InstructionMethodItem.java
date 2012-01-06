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

import org.jf.baksmali.Adaptors.MethodItem;
import org.jf.baksmali.Adaptors.ReferenceFormatter;
import org.jf.baksmali.Adaptors.RegisterFormatter;
import org.jf.baksmali.Renderers.LongRenderer;
import org.jf.dexlib.Code.*;
import org.jf.dexlib.Code.Format.Instruction20bc;
import org.jf.dexlib.*;
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

        boolean debug = false;
//        System.out.println(instruction.opcode.toString());
        if (value == 0x0) { // nop
            writeOpcode(writer);
        } else if (value >= 0x01 && value <= 0x09) { //moves
            if (debug) {
                writer.write("//moves\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeSecondRegister(writer);
            setFirstRegisterContents(writer, getSecondRegisterContents());
        } else if (value >= 0x0a && value <= 0x0d) { //move-result
            if (previousMethodCall != null) {
                setFirstRegisterContents(writer, previousMethodCall);
                previousMethodCall = null;
            } else {
                RegisterFormatter.clearRegisterContents(getFirstRegister());
                writeOpcode(writer);
                writer.write(' ');
                writeFirstRegister(writer);
            }
        } else if (value == 0x0e) { // return-void
            if (debug) {
                writer.write("//return-void\n");
            }
            writeOpcode(writer);
        } else if (value >= 0xf && value <= 0x011) { //return value
            if (debug) {
                writer.write("//return value\n");
            }
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
            int register = getFirstRegister();
            if (!RegisterFormatter.isLocal(register)) {
                writer.write(" //return ");
                writer.write(RegisterFormatter.getRegisterName(codeItem, register));
            }
        } else if (value >= 0x012 && value <= 0x019) { //const primitive
            if (debug) {
                writer.write("//const primitive\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeLiteral(writer);
            setFirstRegisterContents(writer, getLiteral());
        } else if (value >= 0x01a && value <= 0x01c) { //const string, class
            if (debug) {
                writer.write("//const string, class\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeReference(writer);
            setFirstRegisterContents(writer, getReference());
//        } else if (value >= 0x01d && value <= 0x01e) {
            // Todo: Skipped monitor opcodes
        } else if (value == 0x01f) { //check-cast
            if (debug) {
                writer.write("//check-cast\n");
            }
//            writeFirstRegister(writer);
//            writer.write(" = (");
//            writeReference(writer);
//            writer.write(") ");
//            writeFirstRegister(writer);
            setFirstRegisterContents(writer, "(" + getReference() + ") " + getFirstRegisterContents());
        } else if (value == 0x020) { // instanceof
            if (debug) {
                writer.write("//instanceof\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeSecondRegister(writer);
//            writeOpcode(writer);
//            writer.write(' ');
//            writeReference(writer);
            setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getReference());
        } else if (value == 0x021) { //array-length
            if (debug) {
                writer.write("//array-length\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeSecondRegister(writer);
//            writeOpcode(writer);
            setFirstRegisterContents(writer, getSecondRegisterContents() + getOpcode());
        } else if (value == 0x022) { // new instance
            RegisterFormatter.clearRegisterContents(getFirstRegister());
//            writeOpcode(writer);
//            writer.write(' ');
//            writeFirstRegister(writer);
//            writer.write(", ");
//            writeReference(writer);
        } else if (value == 0x023) { // new-array
            if (debug) {
                writer.write("//new-array\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writer.write("new ");
//            writeReference(writer);
//            writer.write('[');
//            writeSecondRegister(writer);
//            writer.write(']');
            String typeReference = getReference();
            setFirstRegisterContents(writer, "new " + typeReference.substring(0, typeReference.length() - 2) + "[" + getSecondRegisterContents() + "]");
//        } else if (value >= 0x024 && value <= 0x026) {
            // Todo: Skipped filled array opcodes
        } else if (value == 0x027) { // throw
            if (debug) {
                writer.write("//throw\n");
            }
            writeOpcode(writer);
            writer.write(' ');
            writeFirstRegister(writer);
        } else if (value >= 0x028 && value <= 0x02a) { //goto
            if (debug) {
                writer.write("//goto\n");
            }
            if (previousNonPrintingInstruction != null) {
                writer.write(previousNonPrintingInstruction);
                writer.write(";\n");
            }
            writer.write("//");
            writeOpcode(writer);
            writer.write(' ');
            writeTargetLabel(writer);
            writer.write("\n\n");
//        } else if (value == 0x02b) {
            // Todo: Skipped packed switch opcodes
//        } else if (value == 0x02c) {
            // Todo: Skipped sparse switch opcodes
//        } else if (value >= 0x02d && value <= 0x031) {
            // Todo: Skipped compares
        } else if (value >= 0x032 && value <= 0x037) { //if compare to reg
            if (debug) {
                writer.write("//if compare to reg\n");
            }
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
            if (debug) {
                writer.write("//if compare to zero\n");
            }
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
            if (debug) {
                writer.write("//aget\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeSecondRegister(writer);
//            writer.write('[');
//            writeThirdRegister(writer);
//            writer.write(']');
            setFirstRegisterContents(writer, getSecondRegisterContents() + "[" + getThirdRegisterContents() + "]");
        } else if (value >= 0x04b && value <= 0x051) { //aput
            if (debug) {
                writer.write("//aput\n");
            }
            writeSecondRegister(writer);
            writer.write('[');
            writeThirdRegister(writer);
            writer.write(']');
            writeEquals(writer);
            writeFirstRegister(writer);
        } else if (value >= 0x051 && value <= 0x058) { //iget
            if (debug) {
                writer.write("//iget\n");
            }
//            int parameterRegisterCount = codeItem.getParent().method.getPrototype().getParameterRegisterCount()
//                    + (((codeItem.getParent().accessFlags & AccessFlags.STATIC.getValue()) == 0) ? 1 : 0);
//            int registerCount = codeItem.getRegisterCount();
//            int secondRegister = ((TwoRegisterInstruction) instruction).getRegisterB();

//            writeFirstRegister(writer);
//            writeEquals(writer);
            String contents = getReference();
            String secondRegister = getSecondRegisterContents();
            if (!secondRegister.equals("this")) {
                contents = getSecondRegisterContents() + "." + contents;
//                writeSecondRegister(writer);
//                writer.write('.');
            }
//            writeReference(writer);
            setFirstRegisterContents(writer, contents);
        } else if (value >= 0x059 && value <= 0x05f) { //iput
            if (debug) {
                writer.write("//iput\n");
            }
            int parameterRegisterCount = codeItem.getParent().method.getPrototype().getParameterRegisterCount()
                    + (((codeItem.getParent().accessFlags & AccessFlags.STATIC.getValue()) == 0) ? 1 : 0);
            int registerCount = codeItem.getRegisterCount();
            int secondRegister = ((TwoRegisterInstruction) instruction).getRegisterB();
            if (secondRegister != registerCount - parameterRegisterCount) {
                writeSecondRegister(writer);
                writer.write('.');
            }
            writeReference(writer);
            writeEquals(writer);
            writeFirstRegister(writer);
        } else if (value >= 0x060 && value <= 0x066) { //sget
            if (debug) {
                writer.write("//sget\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeReference(writer);
            setFirstRegisterContents(writer, getReference());
        } else if (value >= 0x067 && value <= 0x06d) { //sput
            if (debug) {
                writer.write("//sput\n");
            }
            writeReference(writer);
            writeEquals(writer);
            writeFirstRegister(writer);
        } else if (value >= 0x06e && value <= 0x072 && value != 0x071) { //invoke non-static
            if (debug) {
                writer.write("//invoke non-static\n");
            }
//            writeInstance(writer);
//            writer.write('.');
//            writeReference(writer);
//            writeInvokeRegisters(writer);
            String methodName = ((MethodIdItem) (((InstructionWithReference) instruction).getReferencedItem())).getMethodName().getStringValue();
            String instance = getInstance();
            if (methodName.equals("<init>") && !instance.equals("this")) {
                setRegisterContents(writer, getInstanceRegister(), "new " + getReference() + getInvocation());
            } else {
                previousMethodCall = getReference() + getInvocation();
                if (!instance.equals("this")) {
                    previousMethodCall = instance + "." + previousMethodCall;
                }
            }
        } else if (value == 0x071) { //invoke static
            if (debug) {
                writer.write("//invoke static\n");
            }
//            writeReferenceStatic(writer);
//            writeStaticInvokeRegisters(writer);
            previousMethodCall = getStaticReference() + getStaticInvocation();
        } else if (value >= 0x074 && value <= 0x078 && value != 77) { // invoke-range non-static
            if (debug) {
                writer.write("//invoke-range non-static\n");
            }
//            writeRangeInstance(writer);
//            writer.write('.');
//            writeReference(writer);
//            writeInvokeRangeRegisters(writer);
            String instance = getRangeInstance();
            previousMethodCall = getReference() + getRangeInvocation();
            if (!instance.equals("this")) {
                previousMethodCall = instance + "." + previousMethodCall;
            }
        } else if (value == 77) { // invoke-range static
            if (debug) {
                writer.write("//invoke-range static\n");
            }
//            writeReference(writer);
//            writeStaticInvokeRangeRegisters(writer);
            previousMethodCall = getStaticReference() + getStaticRangeInvocation();
        } else if (value >= 0x07b && value <= 0x08f) { //conversions
            if (debug) {
                writer.write("//conversions\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeOpcode(writer);
//            writeSecondRegister(writer);
            setFirstRegisterContents(writer, getOpcode() + getSecondRegisterContents());
        } else if (value >= 0x090 && value <= 0x0af) { //basic arithmetic
            if (debug) {
                writer.write("//basic arithmetic\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeSecondRegister(writer);
//            writer.write(' ');
//            writeOpcode(writer);
//            writer.write(' ');
//            writeThirdRegister(writer);
            setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getThirdRegisterContents());
        } else if (value >= 0x0b0 && value <= 0x0cf) { //arithmetic, store in first
            if (debug) {
                writer.write("//arithmetic, store in first\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeFirstRegister(writer);
//            writer.write(' ');
//            writeOpcode(writer);
//            writer.write(' ');
//            writeSecondRegister(writer);
            setFirstRegisterContents(writer, getFirstRegisterContents() + " " + getOpcode() + " " + getSecondRegisterContents());
        } else if (value >= 0x0d0 && value <= 0x0e2) { //literal arithmetic
            if (debug) {
                writer.write("//literal arithmetic\n");
            }
//            writeFirstRegister(writer);
//            writeEquals(writer);
//            writeSecondRegister(writer);
//            writer.write(' ');
//            writeOpcode(writer);
//            writer.write(' ');
//            writeLiteral(writer);
            setFirstRegisterContents(writer, getSecondRegisterContents() + " " + getOpcode() + " " + getLiteral());
        } else {
            if (debug) {
                writer.write("//not explicitly caught\n");
            }
            switch (instruction.getFormat()) {
                case Format10t: //goto
                    writer.write("//");
                    writeOpcode(writer);
                    writer.write(' ');
                    writeTargetLabel(writer);
                    return true;
                case Format10x: //return-void, nop
                    writeOpcode(writer);
                    return true;
                case Format11n: //const/4
//                writeOpcode(writer);
//                writer.write(' ');
                    writeFirstRegister(writer);
                    writeEquals(writer);
                    writeLiteral(writer);
                    return true;
                case Format11x: // monitor, return, throw
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    return true;
                case Format12x: // array-length
//                writeOpcode(writer);
//                writer.write(' ');
                    writeFirstRegister(writer);
                    writeEquals(writer);
                    writeSecondRegister(writer);
                    writer.write(".length()");
                    return true;
                case Format20bc: //throw-verification-error
                    writeOpcode(writer);
                    writer.write(' ');
                    writeVerificationErrorType(writer);
                    writer.write(", ");
                    writeReference(writer);
                    return true;
                case Format20t: //goto_16
                case Format30t: //goto_32
                    writer.write("//");
                    writeOpcode(writer);
                    writer.write(' ');
                    writeTargetLabel(writer);
                    return true;
                case Format21c: //sget
                case Format31c: //const-string/jumbo
                case Format41c: //
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeReference(writer);
                    return true;
                case Format21h:
                case Format21s:
                case Format31i:
                case Format51l:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeLiteral(writer);
                    return true;
                case Format21t:
                case Format31t:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeTargetLabel(writer);
                    return true;
                case Format22b:
                case Format22s:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeSecondRegister(writer);
                    writer.write(", ");
                    writeLiteral(writer);
                    return true;
                case Format22c:
                case Format52c:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeSecondRegister(writer);
                    writer.write(", ");
                    writeReference(writer);
                    return true;
                case Format22cs:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeSecondRegister(writer);
                    writer.write(", ");
                    writeFieldOffset(writer);
                    return true;
                case Format22t:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeSecondRegister(writer);
                    writer.write(", ");
                    writeTargetLabel(writer);
                    return true;
                case Format22x:
                case Format32x:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeSecondRegister(writer);
                    return true;
                case Format23x:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeFirstRegister(writer);
                    writer.write(", ");
                    writeSecondRegister(writer);
                    writer.write(", ");
                    writeThirdRegister(writer);
                    return true;
                case Format35c:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeInvokeRegisters(writer);
                    writer.write(", ");
                    writeReference(writer);
                    return true;
                case Format35mi:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeInvokeRegisters(writer);
                    writer.write(", ");
                    writeInlineIndex(writer);
                    return true;
                case Format35ms:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeInvokeRegisters(writer);
                    writer.write(", ");
                    writeVtableIndex(writer);
                    return true;
                case Format3rc:
                case Format5rc:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeInvokeRangeRegisters(writer);
                    writer.write(", ");
                    writeReference(writer);
                    return true;
                case Format3rmi:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeInvokeRangeRegisters(writer);
                    writer.write(", ");
                    writeInlineIndex(writer);
                    return true;
                case Format3rms:
                    writeOpcode(writer);
                    writer.write(' ');
                    writeInvokeRangeRegisters(writer);
                    writer.write(", ");
                    writeVtableIndex(writer);
                    return true;
            }
            assert false;
            return false;
        }
        return true;
    }

    private void writeEquals(IndentingWriter writer) throws IOException {
        writer.write(" = ");
    }

    protected void writeOpcode(IndentingWriter writer) throws IOException {
        writer.write(instruction.opcode.name);
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

    protected void writeStaticInvokeRegisters(IndentingWriter writer) throws IOException {
        FiveRegisterInstruction instruction = (FiveRegisterInstruction) this.instruction;
        final int regCount = instruction.getRegCount();

        writer.write('(');
        switch (regCount) {
            case 1:
                writeRegister(writer, instruction.getRegisterD());
                break;
            case 2:
                writeRegister(writer, instruction.getRegisterD());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterE());
                break;
            case 3:
                writeRegister(writer, instruction.getRegisterD());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterE());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterF());
                break;
            case 4:
                writeRegister(writer, instruction.getRegisterD());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterE());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterF());
                writer.write(", ");
                writeRegister(writer, instruction.getRegisterG());
                break;
            case 5:
                writeRegister(writer, instruction.getRegisterD());
                writer.write(", ");
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

    protected void writeStaticInvokeRangeRegisters(IndentingWriter writer) throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;

        int regCount = instruction.getRegCount();
        if (regCount == 0) {
            writer.write("()");
        } else {
            int startRegister = instruction.getStartRegister();
            RegisterFormatter.writeRegisterRange(writer, codeItem, startRegister, startRegister + regCount - 1);
        }
    }

    private void writeInstance(IndentingWriter writer) throws IOException {
        FiveRegisterInstruction instruction = (FiveRegisterInstruction) this.instruction;
        writeRegister(writer, instruction.getRegisterD());
    }

    private void writeRangeInstance(IndentingWriter writer) throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;
        writeRegister(writer, instruction.getStartRegister());
    }

    protected void writeLiteral(IndentingWriter writer) throws IOException {
        LongRenderer.writeSignedIntOrLongTo(writer, ((LiteralInstruction) instruction).getLiteral());
    }

    protected void writeFieldOffset(IndentingWriter writer) throws IOException {
        writer.write("field@0x");
        writer.printUnsignedLongAsHex(((OdexedFieldAccess) instruction).getFieldOffset());
    }

    protected void writeInlineIndex(IndentingWriter writer) throws IOException {
        writer.write("inline@0x");
        writer.printUnsignedLongAsHex(((OdexedInvokeInline) instruction).getInlineIndex());
    }

    protected void writeVtableIndex(IndentingWriter writer) throws IOException {
        writer.write("vtable@0x");
        writer.printUnsignedLongAsHex(((OdexedInvokeVirtual) instruction).getVtableIndex());
    }

    protected void writeReference(IndentingWriter writer) throws IOException {
        Item item = ((InstructionWithReference) instruction).getReferencedItem();
        ReferenceFormatter.writeReference(writer, item);
    }

    protected void writeVerificationErrorType(IndentingWriter writer) throws IOException {
        VerificationErrorType validationErrorType = ((Instruction20bc) instruction).getValidationErrorType();
        writer.write(validationErrorType.getName());
    }

    private void setRegisterContents(IndentingWriter writer, int register, String contents) throws IOException {
        if (RegisterFormatter.isLocal(register)) {
            writeRegister(writer, register);
            writeEquals(writer);
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

    private String getRangeInstance() throws IOException {
        RegisterRangeInstruction instruction = (RegisterRangeInstruction) this.instruction;
        return RegisterFormatter.getRegisterContents(codeItem, instruction.getStartRegister());
    }
}
