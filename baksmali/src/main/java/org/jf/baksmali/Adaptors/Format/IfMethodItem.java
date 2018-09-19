package org.jf.baksmali.Adaptors.Format;

import org.jetbrains.annotations.NotNull;
import org.jf.baksmali.Adaptors.MethodItem;
import org.jf.baksmali.Adaptors.RegisterFormatter;
import org.jf.baksmali.Adaptors.TypeFormatter;
import org.jf.baksmali.Parenthesizer;
import org.jf.dexlib.Code.Instruction;
import org.jf.dexlib.Code.Opcode;
import org.jf.dexlib.Code.SingleRegisterInstruction;
import org.jf.dexlib.Code.TwoRegisterInstruction;
import org.jf.dexlib.CodeItem;
import org.jf.util.IndentingWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IfMethodItem extends MethodItem {
    private static final MethodItem DUMMY_ITEM = new MethodItem(Integer.MAX_VALUE) {
        @Override
        public double getSortOrder() {
            return 0;
        }

        @Override
        public boolean writeTo(IndentingWriter writer) {
            return false;
        }
    };
    private final CodeItem underlyingCodeItem;
    private final Instruction underlyingInstruction;
    public final List<MethodItem> thenItems;
    public final List<MethodItem> elseItems;

    public IfMethodItem(int codeAddress, CodeItem underlyingCodeItem, Instruction underlyingInstruction, List<MethodItem> thenItems, List<MethodItem> elseItems) {
        super(codeAddress);
        this.underlyingCodeItem = underlyingCodeItem;
        this.underlyingInstruction = underlyingInstruction;
        this.thenItems = thenItems;
        this.elseItems = elseItems;
    }

    @Override
    public double getSortOrder() {
        return 0;
    }

    private int getFirstRegister() {
        return ((SingleRegisterInstruction) underlyingInstruction).getRegisterA();
    }

    private String getFirstRegisterContents() {
        return RegisterFormatter.getRegisterContents(((SingleRegisterInstruction) underlyingInstruction).getRegisterA(), underlyingCodeItem);
    }

    private String getSecondRegisterContents() {
        return RegisterFormatter.getRegisterContents(((TwoRegisterInstruction) underlyingInstruction).getRegisterB(), underlyingCodeItem);
    }

    private static void writeItems(IndentingWriter writer, List<MethodItem> items) throws IOException {
        for (MethodItem item : items) {
            if (item.write(writer)) {
                writer.write(";\n");
            }
        }
        DUMMY_ITEM.write(writer);
    }

    @Override
    public boolean writeTo(IndentingWriter writer) throws IOException {

        // First determine if we need to swap our then and else blocks
        final Opcode opcode;
        final List<MethodItem> thenItems;
        final List<MethodItem> elseItems;
        if (this.thenItems == null || this.thenItems.size() == 0) {
            thenItems = this.elseItems;
            elseItems = this.thenItems;
            opcode = flipOpcode(underlyingInstruction.opcode);
        } else {
            thenItems = this.thenItems;
            elseItems = this.elseItems;
            opcode = underlyingInstruction.opcode;
        }
        
        writer.write("if (");
        writeCondition(writer, opcode);
        writer.write(") {\n");
        writer.indent(4);
        writeItems(writer, thenItems);
        if (elseItems != null && elseItems.size() > 0) {
            writer.deindent(4);
            writer.write("} else {\n");
            writer.indent(4);
            writeItems(writer, elseItems);
        }
        writer.deindent(4);
        writer.write("}\n");
        return false;
    }

    private void writeCondition(IndentingWriter writer, Opcode opcode) throws IOException {
        final String other;
        if (opcode.value >= 0x032 && opcode.value <= 0x037) {
            // Compare to register
            other = getSecondRegisterContents();
        } else {
            // Compare to zero
            String registerType = RegisterFormatter.getRegisterType(getFirstRegister());
            if ("Z".equals(registerType)) {
                // Special case for comparing against a boolean, so we don't write things like " == false"
                if (opcode == Opcode.IF_EQZ) {
                    writer.write("!");
                    writer.write(getFirstRegisterContents());
                } else if (opcode == Opcode.IF_NEZ) {
                    writer.write(getFirstRegisterContents());
                } else {
                    throw new RuntimeException("Unexpected opcode: " + opcode);
                }
                return;
            }
            other = TypeFormatter.zeroAs(registerType);
        }
        writer.write(Parenthesizer.ensureOrderOfOperations(opcode.name, getFirstRegisterContents(), other));
    }

    private Opcode flipOpcode(Opcode opcode) {
        switch (opcode) {
            case IF_EQ:
                return Opcode.IF_NE;
            case IF_NE:
                return Opcode.IF_EQ;
            case IF_LT:
                return Opcode.IF_GE;
            case IF_GE:
                return Opcode.IF_LT;
            case IF_GT:
                return Opcode.IF_LE;
            case IF_LE:
                return Opcode.IF_GT;
            case IF_EQZ:
                return Opcode.IF_NEZ;
            case IF_NEZ:
                return Opcode.IF_EQZ;
            case IF_LTZ:
                return Opcode.IF_GEZ;
            case IF_GEZ:
                return Opcode.IF_LTZ;
            case IF_GTZ:
                return Opcode.IF_LEZ;
            case IF_LEZ:
                return Opcode.IF_GTZ;
            default:
                throw new RuntimeException("Unexpected opcode: " + opcode);
        }
    }

    @NotNull
    public IfMethodItem withNewItems(@NotNull List<MethodItem> thenItems, @NotNull List<MethodItem> elseItems) {
        return new IfMethodItem(
                this.codeAddress,
                this.underlyingCodeItem,
                this.underlyingInstruction,
                thenItems,
                elseItems
        );
    }

    @NotNull
    public static IfMethodItem emptyFromOffsetInstruction(@NotNull OffsetInstructionFormatMethodItem<?> offsetItem) {
        return new IfMethodItem(
                offsetItem.getCodeAddress(),
                InstructionMethodItem.getCodeItem(),
                offsetItem.instruction,
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
