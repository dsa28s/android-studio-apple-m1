/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package org.graalvm.compiler.lir.sparc;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.CC;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;

public class SPARCFloatCompareOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCFloatCompareOp> TYPE = LIRInstructionClass.create(SPARCFloatCompareOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(1);

    private final CC cc;
    @Opcode protected final Opfs opf;
    @Use({REG}) protected AllocatableValue a;
    @Use({REG}) protected AllocatableValue b;

    public SPARCFloatCompareOp(Opfs opf, CC cc, AllocatableValue a, AllocatableValue b) {
        super(TYPE, SIZE);
        this.cc = cc;
        this.opf = opf;
        this.a = a;
        this.b = b;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        getDelayedControlTransfer().emitControlTransfer(crb, masm);
        SPARCAssembler.OpfOp.emitFcmp(masm, opf, cc, asRegister(a), asRegister(b));
    }

    @Override
    public void verify() {
        assert a.getPlatformKind().equals(b.getPlatformKind()) : "a: " + a + " b: " + b;
        assert opf.equals(Opfs.Fcmpd) || opf.equals(Opfs.Fcmps) : opf;
    }
}
