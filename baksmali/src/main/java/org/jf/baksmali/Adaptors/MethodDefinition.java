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

import org.jf.baksmali.Adaptors.Format.InstructionMethodItemFactory;
import org.jf.baksmali.baksmali;
import org.jf.dexlib.*;
import org.jf.dexlib.Code.Analysis.AnalyzedInstruction;
import org.jf.dexlib.Code.Analysis.MethodAnalyzer;
import org.jf.dexlib.Code.Analysis.SyntheticAccessorResolver;
import org.jf.dexlib.Code.Analysis.ValidationException;
import org.jf.dexlib.Code.Format.Format;
import org.jf.dexlib.Code.Instruction;
import org.jf.dexlib.Code.InstructionWithReference;
import org.jf.dexlib.Code.OffsetInstruction;
import org.jf.dexlib.Code.Opcode;
import org.jf.dexlib.Debug.DebugInstructionIterator;
import org.jf.dexlib.Util.AccessFlags;
import org.jf.dexlib.Util.ExceptionWithContext;
import org.jf.dexlib.Util.SparseIntArray;
import org.jf.util.IndentingWriter;

import java.io.IOException;
import java.util.*;

public class MethodDefinition {
    private final ClassDataItem.EncodedMethod encodedMethod;
    private MethodAnalyzer methodAnalyzer;

    private static List<String> parameterTypes = null;
    private static String name = null;
    private static TypeIdItem returnType = null;

    private final LabelCache labelCache = new LabelCache();

    private final SparseIntArray packedSwitchMap;
    private final SparseIntArray sparseSwitchMap;
    private final SparseIntArray instructionMap;

    public MethodDefinition(ClassDataItem.EncodedMethod encodedMethod) {


        try {
            this.encodedMethod = encodedMethod;

            //TODO: what about try/catch blocks inside the dead code? those will need to be commented out too. ugh.

            if (encodedMethod.codeItem != null) {
                Instruction[] instructions = encodedMethod.codeItem.getInstructions();

                packedSwitchMap = new SparseIntArray(1);
                sparseSwitchMap = new SparseIntArray(1);
                instructionMap = new SparseIntArray(instructions.length);

                int currentCodeAddress = 0;
                for (int i = 0; i < instructions.length; i++) {
                    Instruction instruction = instructions[i];
                    if (instruction.opcode == Opcode.PACKED_SWITCH) {
                        packedSwitchMap.append(
                                currentCodeAddress +
                                        ((OffsetInstruction) instruction).getTargetAddressOffset(),
                                currentCodeAddress);
                    } else if (instruction.opcode == Opcode.SPARSE_SWITCH) {
                        sparseSwitchMap.append(
                                currentCodeAddress +
                                        ((OffsetInstruction) instruction).getTargetAddressOffset(),
                                currentCodeAddress);
                    }
                    instructionMap.append(currentCodeAddress, i);
                    currentCodeAddress += instruction.getSize(currentCodeAddress);
                }
            } else {
                packedSwitchMap = null;
                sparseSwitchMap = null;
                instructionMap = null;
                methodAnalyzer = null;
            }
        } catch (Exception ex) {
            throw ExceptionWithContext.withContext(ex, String.format("Error while processing method %s",
                    encodedMethod.method.getMethodString()));
        }
    }

    private List<String> getDalvikParameterTypes() {
        List<TypeIdItem> typeIdItems = encodedMethod.method.getPrototype().getParameterTypes();
        ArrayList<String> dalvikTypes = new ArrayList<String>(typeIdItems.size());
        for (TypeIdItem typeIdItem : typeIdItems) {
            dalvikTypes.add(typeIdItem.getTypeDescriptor());
        }
        return dalvikTypes;
    }

    private List<String> getSignatureParameterTypes() {
        if (parameterTypes == null) {
            List<TypeIdItem> typeIdItems = encodedMethod.method.getPrototype().getParameterTypes();
            parameterTypes = new ArrayList<String>(typeIdItems.size());
            for (TypeIdItem typeIdItem : typeIdItems) {
                parameterTypes.add(TypeFormatter.getType(typeIdItem));
            }
        }
        return parameterTypes;
    }

    public static void setParameterTypes(List<String> parameterTypes) {
        MethodDefinition.parameterTypes = parameterTypes;
    }

    public static String getName() {
        return name;
    }

    public static String getDalvikReturnType() {
        return returnType.getTypeDescriptor();
    }

    public void writeTo(IndentingWriter writer, AnnotationSetItem annotationSet,
                        AnnotationSetRefList parameterAnnotations) throws IOException {
        final CodeItem codeItem = encodedMethod.codeItem;

        name = encodedMethod.method.getMethodName().getStringValue();
        returnType = encodedMethod.method.getPrototype().getReturnType();

        //don't print synthetic methods
        if (AccessFlags.hasFlag(encodedMethod.accessFlags, AccessFlags.SYNTHETIC)) {
            return;
        }

        if (codeItem != null) {
            RegisterFormatter.newRegisterSet(getRegisterCount(encodedMethod));
        } else {
            RegisterFormatter.clearRegisters();
        }

        writeAccessFlags(writer, encodedMethod);
        if (AccessFlags.hasFlag(encodedMethod.accessFlags, AccessFlags.CONSTRUCTOR)) {
            writer.write(TypeFormatter.getType(encodedMethod.method.getContainingClass()));
        } else {
            if (!SignatureFormatter.writeSignature(writer, annotationSet, SignatureFormatter.Origin.Method)) {
                writer.write(TypeFormatter.getType(returnType));
            }
            writer.write(' ');
            writer.write(encodedMethod.method.getMethodName().getStringValue());
        }
        writeSignature(writer, codeItem, parameterAnnotations);


        if (AccessFlags.hasFlag(encodedMethod.accessFlags, AccessFlags.NATIVE) ||
                ClassDefinition.isInterface()) {
            writer.write(";\n");
        } else {
            writer.write(" {");

            writer.indent(4);
            if (codeItem != null) {
                if (annotationSet != null) {
                    AnnotationFormatter.writeTo(writer, annotationSet);
                }

                writer.write('\n');

                for (MethodItem methodItem : getMethodItems()) {
                    if (methodItem.writeTo(writer)) {
                        writer.write(";\n");
                    }
                }
            } else {
                if (annotationSet != null) {
                    AnnotationFormatter.writeTo(writer, annotationSet);
                }
            }
            writer.deindent(4);
            writer.write("}\n");
        }
    }

    private void writeSignature(IndentingWriter writer, CodeItem codeItem, AnnotationSetRefList annotationSet) throws IOException {
        writer.write('(');
        List<String> signatureParameterTypes = getSignatureParameterTypes();
        List<String> dalvikParameterTypes = getDalvikParameterTypes();
        LinkedList<String> parameterNames = getParameterNames(codeItem, annotationSet);

        int firstParameter = -1;
        if (encodedMethod.codeItem != null) {
            firstParameter = getRegisterCount(encodedMethod) - signatureParameterTypes.size();
        }

        if (firstParameter >= 0 && "this".equals(parameterNames.peekFirst())) {
            RegisterFormatter.setRegisterContents(firstParameter - 1, parameterNames.removeFirst(), ClassDefinition.getDalvikClassName());
        }

        if (parameterNames.size() != signatureParameterTypes.size()) {
            parameterNames = null;
        }

        boolean first = true;
        for (int i = 0; i < signatureParameterTypes.size(); i++) {
            if (!first) {
                writer.write(", ");
            }
            first = false;

            String type = signatureParameterTypes.get(i);
            String name = null;
            if (parameterNames != null) {
                name = parameterNames.removeFirst();
            }
            if (firstParameter >= 0) {
                // If name is null we want to go ahead and set the contents as null
                RegisterFormatter.setRegisterContents(firstParameter + i, name, dalvikParameterTypes.get(i));
                RegisterFormatter.setLocal(firstParameter + i, true); //Todo: should vars with null names be set as local?
            }
            if (name == null) {
                // But we want to print it's 'name'
                name = "p" + (i + 1);
            }

            writer.write(type);
            writer.write(' ');
            writer.write(name);
        }
        writer.write(')');
        setParameterTypes(null);
    }

    private static int getRegisterCount(ClassDataItem.EncodedMethod encodedMethod) {
        int totalRegisters = encodedMethod.codeItem.getRegisterCount();
        if (baksmali.useLocalsDirective) {
            int parameterRegisters = encodedMethod.method.getPrototype().getParameterRegisterCount();
            if (!AccessFlags.hasFlag(encodedMethod.accessFlags, AccessFlags.STATIC)) {
                parameterRegisters++;
            }
            return totalRegisters - parameterRegisters;
        }
        return totalRegisters;
    }

    private static void writeAccessFlags(IndentingWriter writer, ClassDataItem.EncodedMethod encodedMethod)
            throws IOException {
        boolean isInterface = ClassDefinition.isInterface();
        for (AccessFlags accessFlag : AccessFlags.getAccessFlagsForMethod(encodedMethod.accessFlags)) {
            if (accessFlag.equals(AccessFlags.PUBLIC) ||
                    accessFlag.equals(AccessFlags.PRIVATE) ||
                    accessFlag.equals(AccessFlags.PROTECTED) ||
                    accessFlag.equals(AccessFlags.STATIC) ||
                    accessFlag.equals(AccessFlags.FINAL) ||
                    accessFlag.equals(AccessFlags.SYNCHRONIZED) ||
                    accessFlag.equals(AccessFlags.NATIVE) ||
                    accessFlag.equals(AccessFlags.ABSTRACT) ||
                    accessFlag.equals(AccessFlags.STRICTFP) ||
                    (accessFlag.equals(AccessFlags.ABSTRACT) && !isInterface)) {
                writer.write(accessFlag.toString());
                writer.write(' ');
            }
        }
    }

    private LinkedList<String> getParameterNames(CodeItem codeItem, AnnotationSetRefList parameterAnnotations)
            throws IOException {
        int parameterCount = 0;
        StringIdItem[] parameterNames = new StringIdItem[0];
        DebugInfoItem debugInfoItem = null;

        if (baksmali.outputDebugInfo && codeItem != null) {
            debugInfoItem = codeItem.getDebugInfo();
        }
        if (debugInfoItem != null) {
            parameterNames = debugInfoItem.getParameterNames();
            if (parameterNames == null) {
                parameterNames = new StringIdItem[0];
            }
        }

        if (parameterAnnotations != null) {
            parameterCount = parameterAnnotations.getAnnotationSets().length;
        }
        if (parameterCount < parameterNames.length) {
            parameterCount = parameterNames.length;
        }

        LinkedList<String> rtn = new LinkedList<String>();

        if (!AccessFlags.hasFlag(encodedMethod.accessFlags, AccessFlags.STATIC)) {
            rtn.add("this");
        }

        for (int i = 0; i < parameterCount; i++) {
            StringIdItem parameterName = null;
            if (i < parameterNames.length) {
                parameterName = parameterNames[i];
            }

            if (parameterName != null) {
                rtn.add(parameterName.getStringValue());
            } else {
                rtn.add(null);
            }
        }
        return rtn;
    }

    public LabelCache getLabelCache() {
        return labelCache;
    }

    public ValidationException getValidationException() {
        if (methodAnalyzer == null) {
            return null;
        }

        return methodAnalyzer.getValidationException();
    }

    public int getPackedSwitchBaseAddress(int packedSwitchDataAddress) {
        int packedSwitchBaseAddress = this.packedSwitchMap.get(packedSwitchDataAddress, -1);

        if (packedSwitchBaseAddress == -1) {
            Instruction[] instructions = encodedMethod.codeItem.getInstructions();
            int index = instructionMap.get(packedSwitchDataAddress);

            if (instructions[index].opcode == Opcode.NOP) {
                packedSwitchBaseAddress = this.packedSwitchMap.get(packedSwitchDataAddress + 2, -1);
            }
        }

        return packedSwitchBaseAddress;
    }

    public int getSparseSwitchBaseAddress(int sparseSwitchDataAddress) {
        int sparseSwitchBaseAddress = this.sparseSwitchMap.get(sparseSwitchDataAddress, -1);

        if (sparseSwitchBaseAddress == -1) {
            Instruction[] instructions = encodedMethod.codeItem.getInstructions();
            int index = instructionMap.get(sparseSwitchDataAddress);

            if (instructions[index].opcode == Opcode.NOP) {
                sparseSwitchBaseAddress = this.packedSwitchMap.get(sparseSwitchDataAddress + 2, -1);
            }
        }

        return sparseSwitchBaseAddress;
    }

    /**
     * @param instructions The instructions array for this method
     * @param instruction  The instruction
     * @return true if the specified instruction is a NOP, and the next instruction is one of the variable sized
     *         switch/array data structures
     */
    private boolean isInstructionPaddingNop(List<AnalyzedInstruction> instructions, AnalyzedInstruction instruction) {
        if (instruction.getInstruction().opcode != Opcode.NOP ||
                instruction.getInstruction().getFormat().variableSizeFormat) {

            return false;
        }

        if (instruction.getInstructionIndex() == instructions.size() - 1) {
            return false;
        }

        AnalyzedInstruction nextInstruction = instructions.get(instruction.getInstructionIndex() + 1);
        if (nextInstruction.getInstruction().getFormat().variableSizeFormat) {
            return true;
        }
        return false;
    }

    private List<MethodItem> getMethodItems() {
        ArrayList<MethodItem> methodItems = new ArrayList<MethodItem>();

        if (encodedMethod.codeItem == null) {
            return methodItems;
        }

        if (baksmali.registerInfo != 0 || baksmali.deodex || baksmali.verify) {
            addAnalyzedInstructionMethodItems(methodItems);
        } else {
            addInstructionMethodItems(methodItems);
        }

        addTries(methodItems);
        if (baksmali.outputDebugInfo) {
            addDebugInfo(methodItems);
        }

        if (baksmali.useSequentialLabels) {
            setLabelSequentialNumbers();
        }

        for (LabelMethodItem labelMethodItem : labelCache.getLabels()) {
            methodItems.add(labelMethodItem);
        }

        Collections.sort(methodItems);

        return methodItems;
    }

    private void addInstructionMethodItems(List<MethodItem> methodItems) {
        Instruction[] instructions = encodedMethod.codeItem.getInstructions();

        int currentCodeAddress = 0;
        for (int i = 0; i < instructions.length; i++) {
            Instruction instruction = instructions[i];

            MethodItem methodItem = InstructionMethodItemFactory.makeInstructionFormatMethodItem(this,
                    encodedMethod.codeItem, currentCodeAddress, instruction);

            methodItems.add(methodItem);

            if (baksmali.addCodeOffsets) {
                methodItems.add(new MethodItem(currentCodeAddress) {

                    @Override
                    public double getSortOrder() {
                        return -1000;
                    }

                    @Override
                    public boolean writeTo(IndentingWriter writer) throws IOException {
                        writer.write("#@");
                        writer.printUnsignedLongAsHex(codeAddress & 0xFFFFFFFF);
                        return true;
                    }
                });
            }

            if (!baksmali.noAccessorComments && (instruction instanceof InstructionWithReference)) {
                if (instruction.opcode == Opcode.INVOKE_STATIC || instruction.opcode == Opcode.INVOKE_STATIC_RANGE) {
                    MethodIdItem methodIdItem =
                            (MethodIdItem) ((InstructionWithReference) instruction).getReferencedItem();

                    if (SyntheticAccessorResolver.looksLikeSyntheticAccessor(methodIdItem)) {
                        SyntheticAccessorResolver.AccessedMember accessedMember =
                                baksmali.syntheticAccessorResolver.getAccessedMember(methodIdItem);
                        if (accessedMember != null) {
                            methodItems.add(new SyntheticAccessCommentMethodItem(accessedMember, currentCodeAddress));
                        }
                    }
                }
            }

            currentCodeAddress += instruction.getSize(currentCodeAddress);
        }
    }

    private void addAnalyzedInstructionMethodItems(List<MethodItem> methodItems) {
        methodAnalyzer = new MethodAnalyzer(encodedMethod, baksmali.deodex, baksmali.inlineResolver);

        methodAnalyzer.analyze();

        ValidationException validationException = methodAnalyzer.getValidationException();
        if (validationException != null) {
            methodItems.add(new CommentMethodItem(
                    String.format("ValidationException: %s", validationException.getMessage()),
                    validationException.getCodeAddress(), Integer.MIN_VALUE));
        } else if (baksmali.verify) {
            methodAnalyzer.verify();

            validationException = methodAnalyzer.getValidationException();
            if (validationException != null) {
                methodItems.add(new CommentMethodItem(
                        String.format("ValidationException: %s", validationException.getMessage()),
                        validationException.getCodeAddress(), Integer.MIN_VALUE));
            }
        }

        List<AnalyzedInstruction> instructions = methodAnalyzer.getInstructions();

        int currentCodeAddress = 0;
        for (int i = 0; i < instructions.size(); i++) {
            AnalyzedInstruction instruction = instructions.get(i);

            MethodItem methodItem = InstructionMethodItemFactory.makeInstructionFormatMethodItem(this,
                    encodedMethod.codeItem, currentCodeAddress, instruction.getInstruction());

            methodItems.add(methodItem);

            if (instruction.getInstruction().getFormat() == Format.UnresolvedOdexInstruction) {
                methodItems.add(new CommentedOutMethodItem(
                        InstructionMethodItemFactory.makeInstructionFormatMethodItem(this,
                                encodedMethod.codeItem, currentCodeAddress, instruction.getOriginalInstruction())));
            }

            if (baksmali.addCodeOffsets) {
                methodItems.add(new MethodItem(currentCodeAddress) {

                    @Override
                    public double getSortOrder() {
                        return -1000;
                    }

                    @Override
                    public boolean writeTo(IndentingWriter writer) throws IOException {
                        writer.write("#@");
                        writer.printUnsignedLongAsHex(codeAddress & 0xFFFFFFFF);
                        return true;
                    }
                });
            }

//            if (baksmali.registerInfo != 0 && !instruction.getInstruction().getFormat().variableSizeFormat) {
//                methodItems.add(
//                        new PreInstructionRegisterInfoMethodItem(instruction, methodAnalyzer, currentCodeAddress));
//
//                methodItems.add(
//                        new PostInstructionRegisterInfoMethodItem(instruction, methodAnalyzer, currentCodeAddress));
//            }

            currentCodeAddress += instruction.getInstruction().getSize(currentCodeAddress);
        }
    }

    private void addTries(List<MethodItem> methodItems) {
        if (encodedMethod.codeItem == null || encodedMethod.codeItem.getTries() == null) {
            return;
        }

        Instruction[] instructions = encodedMethod.codeItem.getInstructions();

        for (CodeItem.TryItem tryItem : encodedMethod.codeItem.getTries()) {
            int startAddress = tryItem.getStartCodeAddress();
            int endAddress = tryItem.getStartCodeAddress() + tryItem.getTryLength();

            /**
             * The end address points to the address immediately after the end of the last
             * instruction that the try block covers. We want the .catch directive and end_try
             * label to be associated with the last covered instruction, so we need to get
             * the address for that instruction
             */

            int index = instructionMap.get(endAddress, -1);
            int lastInstructionAddress;

            /**
             * If we couldn't find the index, then the try block probably extends to the last instruction in the
             * method, and so endAddress would be the address immediately after the end of the last instruction.
             * Check to make sure this is the case, if not, throw an exception.
             */
            if (index == -1) {
                Instruction lastInstruction = instructions[instructions.length - 1];
                lastInstructionAddress = instructionMap.keyAt(instructionMap.size() - 1);

                if (endAddress != lastInstructionAddress + lastInstruction.getSize(lastInstructionAddress)) {
                    throw new RuntimeException("Invalid code offset " + endAddress + " for the try block end address");
                }
            } else {
                if (index == 0) {
                    throw new RuntimeException("Unexpected instruction index");
                }
                Instruction lastInstruction = instructions[index - 1];

                if (lastInstruction.getFormat().variableSizeFormat) {
                    throw new RuntimeException("This try block unexpectedly ends on a switch/array data block.");
                }

                //getSize for non-variable size formats should return the same size regardless of code address, so just
                //use a dummy address of "0"
                lastInstructionAddress = endAddress - lastInstruction.getSize(0);
            }

            //add the catch all handler if it exists
            int catchAllAddress = tryItem.encodedCatchHandler.getCatchAllHandlerAddress();
            if (catchAllAddress != -1) {
                CatchMethodItem catchAllMethodItem = new CatchMethodItem(labelCache, lastInstructionAddress, null,
                        startAddress, endAddress, catchAllAddress);
                methodItems.add(catchAllMethodItem);
            }

            //add the rest of the handlers
            for (CodeItem.EncodedTypeAddrPair handler : tryItem.encodedCatchHandler.handlers) {
                //use the address from the last covered instruction
                CatchMethodItem catchMethodItem = new CatchMethodItem(labelCache, lastInstructionAddress,
                        handler.exceptionType, startAddress, endAddress, handler.getHandlerAddress());
                methodItems.add(catchMethodItem);
            }
        }
    }

    private void addDebugInfo(final List<MethodItem> methodItems) {
        if (encodedMethod.codeItem == null || encodedMethod.codeItem.getDebugInfo() == null) {
            return;
        }

        final CodeItem codeItem = encodedMethod.codeItem;
        DebugInfoItem debugInfoItem = codeItem.getDebugInfo();

        DebugInstructionIterator.DecodeInstructions(debugInfoItem, codeItem.getRegisterCount(),
                new DebugInstructionIterator.ProcessDecodedDebugInstructionDelegate() {
                    @Override
                    public void ProcessStartLocal(final int codeAddress, final int length, final int registerNum,
                                                  final StringIdItem name, final TypeIdItem type) {
                        methodItems.add(new DebugMethodItem(codeAddress, -1) {
                            @Override
                            public boolean writeTo(IndentingWriter writer) throws IOException {
                                writeStartLocal(writer, codeItem, registerNum, name, type, null);
                                return true;
                            }
                        });
                    }

                    @Override
                    public void ProcessStartLocalExtended(final int codeAddress, final int length,
                                                          final int registerNum, final StringIdItem name,
                                                          final TypeIdItem type, final StringIdItem signature) {
                        methodItems.add(new DebugMethodItem(codeAddress, -1) {
                            @Override
                            public boolean writeTo(IndentingWriter writer) throws IOException {
                                writeStartLocal(writer, codeItem, registerNum, name, type, signature);
                                return true;
                            }
                        });
                    }

                    @Override
                    public void ProcessEndLocal(final int codeAddress, final int length, final int registerNum,
                                                final StringIdItem name, final TypeIdItem type,
                                                final StringIdItem signature) {
                    }

                    @Override
                    public void ProcessRestartLocal(final int codeAddress, final int length, final int registerNum,
                                                    final StringIdItem name, final TypeIdItem type,
                                                    final StringIdItem signature) {
                    }

                    @Override
                    public void ProcessSetPrologueEnd(int codeAddress) {
                    }

                    @Override
                    public void ProcessSetEpilogueBegin(int codeAddress) {
                    }

                    @Override
                    public void ProcessSetFile(int codeAddress, int length, final StringIdItem name) {
                    }

                    @Override
                    public void ProcessLineEmit(int codeAddress, final int line) {
                    }
                });
    }

    private void setLabelSequentialNumbers() {
        HashMap<String, Integer> nextLabelSequenceByType = new HashMap<String, Integer>();
        ArrayList<LabelMethodItem> sortedLabels = new ArrayList<LabelMethodItem>(labelCache.getLabels());

        //sort the labels by their location in the method
        Collections.sort(sortedLabels);

        for (LabelMethodItem labelMethodItem : sortedLabels) {
            Integer labelSequence = nextLabelSequenceByType.get(labelMethodItem.getLabelPrefix());
            if (labelSequence == null) {
                labelSequence = 0;
            }
            labelMethodItem.setLabelSequence(labelSequence);
            nextLabelSequenceByType.put(labelMethodItem.getLabelPrefix(), labelSequence + 1);
        }
    }

    public static class LabelCache {
        protected HashMap<LabelMethodItem, LabelMethodItem> labels = new HashMap<LabelMethodItem, LabelMethodItem>();

        public LabelCache() {
        }

        public LabelMethodItem internLabel(LabelMethodItem labelMethodItem) {
            LabelMethodItem internedLabelMethodItem = labels.get(labelMethodItem);
            if (internedLabelMethodItem != null) {
                return internedLabelMethodItem;
            }
            labels.put(labelMethodItem, labelMethodItem);
            return labelMethodItem;
        }


        public Collection<LabelMethodItem> getLabels() {
            return labels.values();
        }
    }
}
