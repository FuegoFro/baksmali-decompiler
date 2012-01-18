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

import org.jf.baksmali.baksmali;
import org.jf.dexlib.CodeItem;
import org.jf.dexlib.Util.AccessFlags;
import org.jf.util.IndentingWriter;

import java.io.IOException;

/**
 * This class contains the logic used for formatting registers
 */
public class RegisterFormatter {
    private static String[] registerContents;
    private static boolean[] locals;

    public static void newRegisterSet(int registers) {
        registerContents = new String[registers];
        locals = new boolean[registers];
    }

    public static void clearRegisters() {
        registerContents = null;
        locals = null;
    }

    public static String getRegisterContents(CodeItem codeItem, int register) {
        if (registerContents != null && registerContents[register] != null) {
            return registerContents[register];
        } else {
            return getRegisterName(codeItem, register);
        }
    }

    public static void setRegisterContents(int register, String contents) {
        registerContents[register] = contents;
    }

    public static void clearRegisterContents(int register) {
        if (!isLocal(register)) {
            registerContents[register] = null;
        }
    }

    public static boolean isLocal(int register) {
        return locals[register];
    }

    public static void setLocal(int register, boolean isLocal) {
        locals[register] = isLocal;
    }

    public static String getRegisterName(CodeItem codeItem, int register) {
        if (!baksmali.noParameterRegisters) {
            int parameterRegisterCount = codeItem.getParent().method.getPrototype().getParameterRegisterCount()
                    + (((codeItem.getParent().accessFlags & AccessFlags.STATIC.getValue()) == 0) ? 1 : 0);
            int registerCount = codeItem.getRegisterCount();
            if (register >= registerCount - parameterRegisterCount) {
                return 'p' + String.valueOf((register - (registerCount - parameterRegisterCount)));
            }
        }
        return 'v' + String.valueOf(register);
    }

    public static String getRegisterRange(CodeItem codeItem, int startRegister, int lastRegister) {
        assert lastRegister >= startRegister;

        String range = "(";
        boolean firstTime = true;
        for (int register = startRegister; register <= lastRegister; register++) {
            if (!firstTime) {
                range += ", ";
            }
            firstTime = false;
            range += getRegisterContents(codeItem, register);
        }
        range += ")";
        return range;
    }

    /**
     * Writes a register with the appropriate format. If baksmali.noParameterRegisters is true, then it will always
     * output a register in the v<n> format. If false, then it determines if the register is a parameter register,
     * and if so, formats it in the p<n> format instead.
     *
     * @param writer   the <code>IndentingWriter</code> to write to
     * @param codeItem the <code>CodeItem</code> that the register is from
     * @param register the register number
     */
    public static void writeTo(IndentingWriter writer, CodeItem codeItem, int register) throws IOException {
        writer.write(getRegisterContents(codeItem, register));
    }

    /**
     * Write out the register range value used by Format3rc. If baksmali.noParameterRegisters is true, it will always
     * output the registers in the v<n> format. But if false, then it will check if *both* registers are parameter
     * registers, and if so, use the p<n> format for both. If only the last register is a parameter register, it will
     * use the v<n> format for both, otherwise it would be confusing to have something like {v20 .. p1}
     *
     * @param writer        the <code>IndentingWriter</code> to write to
     * @param codeItem      the <code>CodeItem</code> that the register is from
     * @param startRegister the first register in the range
     * @param lastRegister  the last register in the range
     */
    public static void writeRegisterRange(IndentingWriter writer, CodeItem codeItem, int startRegister,
                                          int lastRegister) throws IOException {
        writer.write(getRegisterRange(codeItem, startRegister, lastRegister));
    }
}
