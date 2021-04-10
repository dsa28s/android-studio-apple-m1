/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.target;

import java.util.ArrayList;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.tiers.TargetProvider;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Represents a compiler backend for Graal.
 */
public abstract class Backend implements TargetProvider, ValueKindFactory<LIRKind> {

    private final Providers providers;
    private final ArrayList<CodeInstallationTaskFactory> codeInstallationTaskFactories;

    public static final ForeignCallDescriptor ARITHMETIC_FREM = new ForeignCallDescriptor("arithmeticFrem", float.class, float.class, float.class);
    public static final ForeignCallDescriptor ARITHMETIC_DREM = new ForeignCallDescriptor("arithmeticDrem", double.class, double.class, double.class);

    protected Backend(Providers providers) {
        this.providers = providers;
        this.codeInstallationTaskFactories = new ArrayList<>();
    }

    public synchronized void addCodeInstallationTask(CodeInstallationTaskFactory factory) {
        this.codeInstallationTaskFactories.add(factory);
    }

    public Providers getProviders() {
        return providers;
    }

    public CodeCacheProvider getCodeCache() {
        return providers.getCodeCache();
    }

    public MetaAccessProvider getMetaAccess() {
        return providers.getMetaAccess();
    }

    public ConstantReflectionProvider getConstantReflection() {
        return providers.getConstantReflection();
    }

    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    public abstract SuitesProvider getSuites();

    @Override
    public TargetDescription getTarget() {
        return providers.getCodeCache().getTarget();
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(getTarget().arch, javaKind);
    }

    /**
     * The given registerConfig is optional, in case null is passed the default RegisterConfig from
     * the CodeCacheProvider will be used.
     */
    public abstract FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig);

    /**
     * Creates a new configuration for register allocation.
     *
     * @param allocationRestrictedTo if not {@code null}, register allocation will be restricted to
     *            registers whose names appear in this array
     */
    public abstract RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo);

    public abstract FrameMap newFrameMap(RegisterConfig registerConfig);

    public abstract LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes);

    public abstract LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, StructuredGraph graph,
                    Object stub);

    public abstract NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen);

    /**
     * Creates the assembler used to emit the machine code.
     */
    protected abstract Assembler createAssembler(FrameMap frameMap);

    /**
     * Creates the object used to fill in the details of a given compilation result.
     */
    public abstract CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult,
                    CompilationResultBuilderFactory factory);

    /**
     * Turns a Graal {@link CompilationResult} into a {@link CompiledCode} object that can be passed
     * to the VM for code installation.
     */
    protected abstract CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult);

    /**
     * @see #createInstalledCode(DebugContext, ResolvedJavaMethod, CompilationRequest,
     *      CompilationResult, SpeculationLog, InstalledCode, boolean, Object[])
     */
    public InstalledCode createInstalledCode(DebugContext debug, ResolvedJavaMethod method, CompilationResult compilationResult,
                    SpeculationLog speculationLog, InstalledCode predefinedInstalledCode, boolean isDefault) {
        return createInstalledCode(debug, method, null, compilationResult, speculationLog, predefinedInstalledCode, isDefault, null);
    }

    /**
     * @see #createInstalledCode(DebugContext, ResolvedJavaMethod, CompilationRequest,
     *      CompilationResult, SpeculationLog, InstalledCode, boolean, Object[])
     */
    @SuppressWarnings("try")
    public InstalledCode createInstalledCode(DebugContext debug, ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult,
                    SpeculationLog speculationLog, InstalledCode predefinedInstalledCode, boolean isDefault) {
        return createInstalledCode(debug, method, compilationRequest, compilationResult, speculationLog, predefinedInstalledCode, isDefault, null);
    }

    /**
     * Installs code based on a given compilation result.
     *
     * @param method the method compiled to produce {@code compiledCode} or {@code null} if the
     *            input to {@code compResult} was not a {@link ResolvedJavaMethod}
     * @param compilationRequest the compilation request or {@code null}
     * @param compilationResult the code to be installed
     * @param predefinedInstalledCode a pre-allocated {@link InstalledCode} object to use as a
     *            reference to the installed code. If {@code null}, a new {@link InstalledCode}
     *            object will be created.
     * @param speculationLog the speculation log to be used
     * @param isDefault specifies if the installed code should be made the default implementation of
     *            {@code compRequest.getMethod()}. The default implementation for a method is the
     *            code executed for standard calls to the method. This argument is ignored if
     *            {@code compRequest == null}.
     * @param context a custom debug context to use for the code installation
     * @return a reference to the compiled and ready-to-run installed code
     * @throws BailoutException if the code installation failed
     */
    @SuppressWarnings("try")
    public InstalledCode createInstalledCode(DebugContext debug, ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult,
                    SpeculationLog speculationLog, InstalledCode predefinedInstalledCode, boolean isDefault, Object[] context) {
        Object[] debugContext = context != null ? context : new Object[]{getProviders().getCodeCache(), method, compilationResult};
        CodeInstallationTask[] tasks;
        synchronized (this) {
            tasks = new CodeInstallationTask[codeInstallationTaskFactories.size()];
            for (int i = 0; i < codeInstallationTaskFactories.size(); i++) {
                tasks[i] = codeInstallationTaskFactories.get(i).create();
            }
        }
        try (DebugContext.Scope s2 = debug.scope("CodeInstall", debugContext);
                        DebugContext.Activation a = debug.activate()) {

            InstalledCode installedCode;
            try {
                preCodeInstallationTasks(tasks, compilationResult, predefinedInstalledCode);
                CompiledCode compiledCode = createCompiledCode(method, compilationRequest, compilationResult);
                installedCode = getProviders().getCodeCache().installCode(method, compiledCode, predefinedInstalledCode, speculationLog, isDefault);
                assert predefinedInstalledCode == null || installedCode == predefinedInstalledCode;
            } catch (Throwable t) {
                failCodeInstallationTasks(tasks, t);
                throw t;
            }

            postCodeInstallationTasks(tasks, installedCode);

            return installedCode;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private static void failCodeInstallationTasks(CodeInstallationTask[] tasks, Throwable t) {
        for (CodeInstallationTask task : tasks) {
            task.installFailed(t);
        }
    }

    private static void preCodeInstallationTasks(CodeInstallationTask[] tasks, CompilationResult compilationResult, InstalledCode predefinedInstalledCode) {
        for (CodeInstallationTask task : tasks) {
            task.preProcess(compilationResult, predefinedInstalledCode);
        }
    }

    private static void postCodeInstallationTasks(CodeInstallationTask[] tasks, InstalledCode installedCode) {
        try {
            for (CodeInstallationTask task : tasks) {
                task.postProcess(installedCode);
            }
        } catch (Throwable t) {
            installedCode.invalidate();
            throw t;
        }
    }

    /**
     * Installs code based on a given compilation result.
     *
     * @param method the method compiled to produce {@code compiledCode} or {@code null} if the
     *            input to {@code compResult} was not a {@link ResolvedJavaMethod}
     * @param compilationRequest the request or {@code null}
     * @param compilationResult the code to be compiled
     * @return a reference to the compiled and ready-to-run installed code
     * @throws BailoutException if the code installation failed
     */
    public InstalledCode addInstalledCode(DebugContext debug, ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult) {
        return createInstalledCode(debug, method, compilationRequest, compilationResult, null, null, false);
    }

    /**
     * Installs code based on a given compilation result and sets it as the default code to be used
     * when {@code method} is invoked.
     *
     * @param method the method compiled to produce {@code compiledCode} or {@code null} if the
     *            input to {@code compResult} was not a {@link ResolvedJavaMethod}
     * @param compilationResult the code to be compiled
     * @return a reference to the compiled and ready-to-run installed code
     * @throws BailoutException if the code installation failed
     */
    public InstalledCode createDefaultInstalledCode(DebugContext debug, ResolvedJavaMethod method, CompilationResult compilationResult) {
        return createInstalledCode(debug, method, compilationResult, null, null, true);
    }

    /**
     * Emits the code for a given graph.
     *
     * @param installedCodeOwner the method the compiled code will be associated with once
     *            installed. This argument can be null.
     */
    public abstract void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner);

    /**
     * Translates a set of registers from the callee's perspective to the caller's perspective. This
     * is needed for architectures where input/output registers are renamed during a call (e.g.
     * register windows on SPARC). Registers which are not visible by the caller are removed.
     */
    public abstract EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters);

    /**
     * Gets the compilation id for a given {@link ResolvedJavaMethod}. Returns
     * {@code CompilationIdentifier#INVALID_COMPILATION_ID} in case there is no such id.
     *
     * @param resolvedJavaMethod
     */
    public CompilationIdentifier getCompilationIdentifier(ResolvedJavaMethod resolvedJavaMethod) {
        return CompilationIdentifier.INVALID_COMPILATION_ID;
    }

    /**
     * Encapsulates custom tasks done before and after code installation.
     */
    public abstract static class CodeInstallationTask {
        /**
         * Task to run before code installation.
         *
         * @param compilationResult the code about to be installed
         * @param predefinedInstalledCode a pre-allocated {@link InstalledCode} object that will be
         *            used as a reference to the installed code. May be {@code null}.
         *
         */
        public void preProcess(CompilationResult compilationResult, InstalledCode predefinedInstalledCode) {
        }

        /**
         * Task to run after the code is installed.
         *
         * @param installedCode a reference to the installed code
         */
        public void postProcess(InstalledCode installedCode) {
        }

        /**
         * Invoked after {@link #preProcess} when code installation fails.
         *
         * @param cause the cause of the installation failure
         */
        public void installFailed(Throwable cause) {
        }
    }

    /**
     * Creates code installation tasks.
     */
    public abstract static class CodeInstallationTaskFactory {
        public abstract CodeInstallationTask create();
    }
}
