/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.Option;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static jdk.vm.ci.hotspot.CompilerToVM.compilerToVM;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.HotSpotModifiers.BRIDGE;
import static jdk.vm.ci.hotspot.HotSpotModifiers.SYNTHETIC;
import static jdk.vm.ci.hotspot.HotSpotModifiers.VARARGS;
import static jdk.vm.ci.hotspot.HotSpotModifiers.jvmMethodModifiers;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

/**
 * Implementation of {@link JavaMethod} for resolved HotSpot methods.
 */
final class HotSpotResolvedJavaMethodImpl extends HotSpotMethod implements HotSpotResolvedJavaMethod, MetaspaceWrapperObject {

    /**
     * Reference to metaspace Method object.
     */
    private final long metaspaceMethod;

    private final HotSpotResolvedObjectTypeImpl holder;
    private final HotSpotConstantPool constantPool;
    private final HotSpotSignature signature;
    private HotSpotMethodData methodData;
    private byte[] code;
    private Executable toJavaCache;

    /**
     * Only 30% of {@link HotSpotResolvedJavaMethodImpl}s have their name accessed so compute it
     * lazily and cache it.
     */
    private String nameCache;

    /**
     * Gets the holder of a HotSpot metaspace method native object.
     *
     * @param metaspaceMethod a metaspace Method object
     * @return the {@link ResolvedJavaType} corresponding to the holder of the
     *         {@code metaspaceMethod}
     */
    private static HotSpotResolvedObjectTypeImpl getHolder(long metaspaceMethod) {
        HotSpotVMConfig config = config();
        final long metaspaceConstMethod = UNSAFE.getAddress(metaspaceMethod + config.methodConstMethodOffset);
        final long metaspaceConstantPool = UNSAFE.getAddress(metaspaceConstMethod + config.constMethodConstantsOffset);
        return compilerToVM().getResolvedJavaType(null, metaspaceConstantPool + config.constantPoolHolderOffset, false);
    }

    /**
     * Gets the JVMCI mirror from a HotSpot method. The VM is responsible for ensuring that the
     * Method* is kept alive for the duration of this call and the
     * {@link HotSpotJVMCIMetaAccessContext} keeps it alive after that.
     *
     * Called from the VM.
     *
     * @param metaspaceMethod a metaspace Method object
     * @return the {@link ResolvedJavaMethod} corresponding to {@code metaspaceMethod}
     */
    @SuppressWarnings("unused")
    private static HotSpotResolvedJavaMethod fromMetaspace(long metaspaceMethod) {
        HotSpotResolvedObjectTypeImpl holder = getHolder(metaspaceMethod);
        return holder.createMethod(metaspaceMethod);
    }

    HotSpotResolvedJavaMethodImpl(HotSpotResolvedObjectTypeImpl holder, long metaspaceMethod) {
        this.metaspaceMethod = metaspaceMethod;
        this.holder = holder;

        HotSpotVMConfig config = config();
        final long constMethod = getConstMethod();

        /*
         * Get the constant pool from the metaspace method. Some methods (e.g. intrinsics for
         * signature-polymorphic method handle methods) have their own constant pool instead of the
         * one from their holder.
         */
        final long metaspaceConstantPool = UNSAFE.getAddress(constMethod + config.constMethodConstantsOffset);
        if (metaspaceConstantPool == holder.getConstantPool().getMetaspaceConstantPool()) {
            this.constantPool = holder.getConstantPool();
        } else {
            this.constantPool = compilerToVM().getConstantPool(this);
        }

        final int signatureIndex = UNSAFE.getChar(constMethod + config.constMethodSignatureIndexOffset);
        this.signature = (HotSpotSignature) constantPool.lookupSignature(signatureIndex);
    }

    /**
     * Returns a pointer to this method's constant method data structure (
     * {@code Method::_constMethod}). This pointer isn't wrapped since it should be safe to use it
     * within the context of this HotSpotResolvedJavaMethodImpl since the Method* and ConstMethod*
     * are kept alive as a pair.
     *
     * @return pointer to this method's ConstMethod
     */
    private long getConstMethod() {
        assert metaspaceMethod != 0;
        return UNSAFE.getAddress(metaspaceMethod + config().methodConstMethodOffset);
    }

    @Override
    public String getName() {
        if (nameCache == null) {
            final int nameIndex = UNSAFE.getChar(getConstMethod() + config().constMethodNameIndexOffset);
            nameCache = constantPool.lookupUtf8(nameIndex);
        }
        return nameCache;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HotSpotResolvedJavaMethodImpl) {
            HotSpotResolvedJavaMethodImpl that = (HotSpotResolvedJavaMethodImpl) obj;
            return that.metaspaceMethod == metaspaceMethod;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) metaspaceMethod;
    }

    /**
     * Returns this method's flags ({@code Method::_flags}).
     *
     * @return flags of this method
     */
    private int getFlags() {
        return UNSAFE.getShort(metaspaceMethod + config().methodFlagsOffset);
    }

    /**
     * Returns this method's constant method flags ({@code ConstMethod::_flags}).
     *
     * @return flags of this method's ConstMethod
     */
    private int getConstMethodFlags() {
        return UNSAFE.getChar(getConstMethod() + config().constMethodFlagsOffset);
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getDeclaringClass() {
        return holder;
    }

    /**
     * Gets the address of the C++ Method object for this method.
     */
    public Constant getMetaspaceMethodConstant() {
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(this, false);
    }

    @Override
    public long getMetaspacePointer() {
        return metaspaceMethod;
    }

    @Override
    public Constant getEncoding() {
        return getMetaspaceMethodConstant();
    }

    /**
     * Gets the complete set of modifiers for this method which includes the JVM specification
     * modifiers as well as the HotSpot internal modifiers.
     */
    public int getAllModifiers() {
        return UNSAFE.getInt(metaspaceMethod + config().methodAccessFlagsOffset);
    }

    @Override
    public int getModifiers() {
        return getAllModifiers() & jvmMethodModifiers();
    }

    @Override
    public boolean canBeStaticallyBound() {
        return (isFinal() || isPrivate() || isStatic() || holder.isLeaf()) && isConcrete();
    }

    @Override
    public byte[] getCode() {
        if (getCodeSize() == 0) {
            return null;
        }
        if (code == null && holder.isLinked()) {
            code = compilerToVM().getBytecode(this);
            assert code.length == getCodeSize() : "expected: " + getCodeSize() + ", actual: " + code.length;
        }
        return code;
    }

    @Override
    public int getCodeSize() {
        return UNSAFE.getChar(getConstMethod() + config().constMethodCodeSizeOffset);
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        final boolean hasExceptionTable = (getConstMethodFlags() & config().constMethodHasExceptionTable) != 0;
        if (!hasExceptionTable) {
            return new ExceptionHandler[0];
        }

        HotSpotVMConfig config = config();
        final int exceptionTableLength = compilerToVM().getExceptionTableLength(this);
        ExceptionHandler[] handlers = new ExceptionHandler[exceptionTableLength];
        long exceptionTableElement = compilerToVM().getExceptionTableStart(this);

        for (int i = 0; i < exceptionTableLength; i++) {
            final int startPc = UNSAFE.getChar(exceptionTableElement + config.exceptionTableElementStartPcOffset);
            final int endPc = UNSAFE.getChar(exceptionTableElement + config.exceptionTableElementEndPcOffset);
            final int handlerPc = UNSAFE.getChar(exceptionTableElement + config.exceptionTableElementHandlerPcOffset);
            int catchTypeIndex = UNSAFE.getChar(exceptionTableElement + config.exceptionTableElementCatchTypeIndexOffset);

            JavaType catchType;
            if (catchTypeIndex == 0) {
                catchType = null;
            } else {
                final int opcode = -1;  // opcode is not used
                catchType = constantPool.lookupType(catchTypeIndex, opcode);

                // Check for Throwable which catches everything.
                if (catchType instanceof HotSpotResolvedObjectTypeImpl) {
                    HotSpotResolvedObjectTypeImpl resolvedType = (HotSpotResolvedObjectTypeImpl) catchType;
                    if (resolvedType.mirror() == Throwable.class) {
                        catchTypeIndex = 0;
                        catchType = null;
                    }
                }
            }
            handlers[i] = new ExceptionHandler(startPc, endPc, handlerPc, catchTypeIndex, catchType);

            // Go to the next ExceptionTableElement
            exceptionTableElement += config.exceptionTableElementSize;
        }

        return handlers;
    }

    /**
     * Returns true if this method has a {@code CallerSensitive} annotation.
     *
     * @return true if CallerSensitive annotation present, false otherwise
     */
    @Override
    public boolean isCallerSensitive() {
        return (getFlags() & config().methodFlagsCallerSensitive) != 0;
    }

    /**
     * Returns true if this method has a {@code ForceInline} annotation.
     *
     * @return true if ForceInline annotation present, false otherwise
     */
    @Override
    public boolean isForceInline() {
        return (getFlags() & config().methodFlagsForceInline) != 0;
    }

    /**
     * Returns true if this method has a {@code ReservedStackAccess} annotation.
     *
     * @return true if ReservedStackAccess annotation present, false otherwise
     */
    @Override
    public boolean hasReservedStackAccess() {
        return (getFlags() & config().methodFlagsReservedStackAccess) != 0;
    }

    /**
     * Sets flags on {@code method} indicating that it should never be inlined or compiled by the VM.
     */
    @Override
    public void setNotInlinableOrCompilable() {
        compilerToVM().setNotInlinableOrCompilable(this);
    }

    /**
     * Returns true if this method is one of the special methods that is ignored by security stack
     * walks.
     *
     * @return true if special method ignored by security stack walks, false otherwise
     */
    @Override
    public boolean ignoredBySecurityStackWalk() {
        return compilerToVM().methodIsIgnoredBySecurityStackWalk(this);
    }

    @Override
    public boolean isClassInitializer() {
        if (isStatic()) {
            final int nameIndex = UNSAFE.getChar(getConstMethod() + config().constMethodNameIndexOffset);
            long nameSymbol = constantPool.getEntryAt(nameIndex);
            long clinitSymbol = config().symbolClinit;
            return nameSymbol == clinitSymbol;
        }
        return false;
    }

    @Override
    public boolean isConstructor() {
        if (!isStatic()) {
            final int nameIndex = UNSAFE.getChar(getConstMethod() + config().constMethodNameIndexOffset);
            long nameSymbol = constantPool.getEntryAt(nameIndex);
            long initSymbol = config().symbolInit;
            return nameSymbol == initSymbol;
        }
        return false;
    }

    @Override
    public int getMaxLocals() {
        if (isAbstract() || isNative()) {
            return 0;
        }
        HotSpotVMConfig config = config();
        return UNSAFE.getChar(getConstMethod() + config.methodMaxLocalsOffset);
    }

    @Override
    public int getMaxStackSize() {
        if (isAbstract() || isNative()) {
            return 0;
        }
        HotSpotVMConfig config = config();
        return config.extraStackEntries + UNSAFE.getChar(getConstMethod() + config.constMethodMaxStackOffset);
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        if (bci < 0 || bci >= getCodeSize()) {
            // HotSpot code can only construct stack trace elements for valid bcis
            StackTraceElement ste = compilerToVM().getStackTraceElement(this, 0);
            return new StackTraceElement(ste.getClassName(), ste.getMethodName(), ste.getFileName(), -1);
        }
        return compilerToVM().getStackTraceElement(this, bci);
    }

    @Override
    public ResolvedJavaMethod uniqueConcreteMethod(HotSpotResolvedObjectType receiver) {
        if (receiver.isInterface()) {
            // Cannot trust interfaces. Because of:
            // interface I { void foo(); }
            // class A { public void foo() {} }
            // class B extends A implements I { }
            // class C extends B { public void foo() { } }
            // class D extends B { }
            // Would lead to identify C.foo() as the unique concrete method for I.foo() without
            // seeing A.foo().
            return null;
        }
        if (this.isDefault()) {
            // CHA for default methods doesn't work and may crash the VM
            return null;
        }
        return compilerToVM().findUniqueConcreteMethod(((HotSpotResolvedObjectTypeImpl) receiver), this);
    }

    @Override
    public HotSpotSignature getSignature() {
        return signature;
    }

    /**
     * Gets the value of {@code Method::_code}.
     *
     * @return the value of {@code Method::_code}
     */
    private long getCompiledCode() {
        HotSpotVMConfig config = config();
        return UNSAFE.getAddress(metaspaceMethod + config.methodCodeOffset);
    }

    /**
     * Returns whether this method has compiled code.
     *
     * @return true if this method has compiled code, false otherwise
     */
    @Override
    public boolean hasCompiledCode() {
        return getCompiledCode() != 0L;
    }

    /**
     * @param level
     * @return true if the currently installed code was generated at {@code level}.
     */
    @Override
    public boolean hasCompiledCodeAtLevel(int level) {
        long compiledCode = getCompiledCode();
        if (compiledCode != 0) {
            return UNSAFE.getInt(compiledCode + config().nmethodCompLevelOffset) == level;
        }
        return false;
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        ProfilingInfo info;

        if (Option.UseProfilingInformation.getBoolean() && methodData == null) {
            long metaspaceMethodData = UNSAFE.getAddress(metaspaceMethod + config().methodDataOffset);
            if (metaspaceMethodData != 0) {
                methodData = new HotSpotMethodData(metaspaceMethodData, this);
                String methodDataFilter = Option.TraceMethodDataFilter.getString();
                if (methodDataFilter != null && this.format("%H.%n").contains(methodDataFilter)) {
                    System.out.println(methodData.toString());
                }
            }
        }

        if (methodData == null || (!methodData.hasNormalData() && !methodData.hasExtraData())) {
            // Be optimistic and return false for exceptionSeen. A methodDataOop is allocated in
            // case of a deoptimization.
            info = DefaultProfilingInfo.get(TriState.FALSE);
        } else {
            info = new HotSpotProfilingInfo(methodData, this, includeNormal, includeOSR);
        }
        return info;
    }

    @Override
    public void reprofile() {
        compilerToVM().reprofile(this);
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public Parameter[] getParameters() {
        Executable javaMethod = toJava();
        if (javaMethod == null) {
            return null;
        }

        java.lang.reflect.Parameter[] javaParameters = javaMethod.getParameters();
        Parameter[] res = new Parameter[javaParameters.length];
        for (int i = 0; i < res.length; i++) {
            java.lang.reflect.Parameter src = javaParameters[i];
            String paramName = src.isNamePresent() ? src.getName() : null;
            res[i] = new Parameter(paramName, src.getModifiers(), this, i);
        }
        return res;
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        Executable javaMethod = toJava();
        return javaMethod == null ? new Annotation[signature.getParameterCount(false)][0] : javaMethod.getParameterAnnotations();
    }

    @Override
    public Annotation[] getAnnotations() {
        Executable javaMethod = toJava();
        if (javaMethod != null) {
            return javaMethod.getAnnotations();
        }
        return new Annotation[0];
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        Executable javaMethod = toJava();
        if (javaMethod != null) {
            return javaMethod.getDeclaredAnnotations();
        }
        return new Annotation[0];
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Executable javaMethod = toJava();
        return javaMethod == null ? null : javaMethod.getAnnotation(annotationClass);
    }

    @Override
    public boolean isBridge() {
        return (BRIDGE & getModifiers()) != 0;
    }

    @Override
    public boolean isSynthetic() {
        return (SYNTHETIC & getModifiers()) != 0;
    }

    @Override
    public boolean isVarArgs() {
        return (VARARGS & getModifiers()) != 0;
    }

    @Override
    public boolean isDefault() {
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        Executable javaMethod = toJava();
        return javaMethod == null ? null : javaMethod.getGenericParameterTypes();
    }

    public Class<?>[] signatureToTypes() {
        Signature sig = getSignature();
        int count = sig.getParameterCount(false);
        Class<?>[] result = new Class<?>[count];
        for (int i = 0; i < result.length; ++i) {
            JavaType parameterType = sig.getParameterType(i, holder);
            HotSpotResolvedJavaType resolvedParameterType = (HotSpotResolvedJavaType) parameterType.resolve(holder);
            result[i] = resolvedParameterType.mirror();
        }
        return result;
    }

    private static Method searchMethods(Method[] methods, String name, Class<?> returnType, Class<?>[] parameterTypes) {
        for (Method m : methods) {
            if (m.getName().equals(name) && returnType.equals(m.getReturnType()) && Arrays.equals(m.getParameterTypes(), parameterTypes)) {
                return m;
            }
        }
        return null;
    }

    private Executable toJava() {
        if (toJavaCache != null) {
            return toJavaCache;
        }
        try {
            Class<?>[] parameterTypes = signatureToTypes();
            Class<?> returnType = ((HotSpotResolvedJavaType) getSignature().getReturnType(holder).resolve(holder)).mirror();

            Executable result;
            if (isConstructor()) {
                result = holder.mirror().getDeclaredConstructor(parameterTypes);
            } else {
                // Do not use Method.getDeclaredMethod() as it can return a bridge method
                // when this.isBridge() is false and vice versa.
                result = searchMethods(holder.mirror().getDeclaredMethods(), getName(), returnType, parameterTypes);
            }
            toJavaCache = result;
            return result;
        } catch (NoSuchMethodException | NoClassDefFoundError e) {
            return null;
        }
    }

    @Override
    public boolean canBeInlined() {
        if (hasNeverInlineDirective()) {
            return false;
        }
        return compilerToVM().isCompilable(this);
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return compilerToVM().hasNeverInlineDirective(this);
    }

    @Override
    public boolean shouldBeInlined() {
        if (isForceInline()) {
            return true;
        }
        return compilerToVM().shouldInlineMethod(this);
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        final boolean hasLineNumberTable = (getConstMethodFlags() & config().constMethodHasLineNumberTable) != 0;
        if (!hasLineNumberTable) {
            return null;
        }

        long[] values = compilerToVM().getLineNumberTable(this);
        if (values == null || values.length == 0) {
            // Empty table so treat is as non-existent
            return null;
        }
        assert values.length % 2 == 0;
        int[] bci = new int[values.length / 2];
        int[] line = new int[values.length / 2];

        for (int i = 0; i < values.length / 2; i++) {
            bci[i] = (int) values[i * 2];
            line[i] = (int) values[i * 2 + 1];
        }

        return new LineNumberTable(line, bci);
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        final boolean hasLocalVariableTable = (getConstMethodFlags() & config().constMethodHasLocalVariableTable) != 0;
        if (!hasLocalVariableTable) {
            return null;
        }

        HotSpotVMConfig config = config();
        long localVariableTableElement = compilerToVM().getLocalVariableTableStart(this);
        final int localVariableTableLength = compilerToVM().getLocalVariableTableLength(this);
        Local[] locals = new Local[localVariableTableLength];

        for (int i = 0; i < localVariableTableLength; i++) {
            final int startBci = UNSAFE.getChar(localVariableTableElement + config.localVariableTableElementStartBciOffset);
            final int endBci = startBci + UNSAFE.getChar(localVariableTableElement + config.localVariableTableElementLengthOffset);
            final int nameCpIndex = UNSAFE.getChar(localVariableTableElement + config.localVariableTableElementNameCpIndexOffset);
            final int typeCpIndex = UNSAFE.getChar(localVariableTableElement + config.localVariableTableElementDescriptorCpIndexOffset);
            final int slot = UNSAFE.getChar(localVariableTableElement + config.localVariableTableElementSlotOffset);

            String localName = getConstantPool().lookupUtf8(nameCpIndex);
            String localType = getConstantPool().lookupUtf8(typeCpIndex);

            locals[i] = new Local(localName, runtime().lookupType(localType, holder, false), startBci, endBci, slot);

            // Go to the next LocalVariableTableElement
            localVariableTableElement += config.localVariableTableElementSize;
        }

        return new LocalVariableTable(locals);
    }

    /**
     * Returns the offset of this method into the v-table. The method must have a v-table entry as
     * indicated by {@link #isInVirtualMethodTable(ResolvedJavaType)}, otherwise an exception is
     * thrown.
     *
     * @return the offset of this method into the v-table
     */
    @Override
    public int vtableEntryOffset(ResolvedJavaType resolved) {
        if (!isInVirtualMethodTable(resolved)) {
            throw new JVMCIError("%s does not have a vtable entry in type %s", this, resolved);
        }
        HotSpotVMConfig config = config();
        final int vtableIndex = getVtableIndex((HotSpotResolvedObjectTypeImpl) resolved);
        return config.klassVtableStartOffset + vtableIndex * config.vtableEntrySize + config.vtableEntryMethodOffset;
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        if (resolved instanceof HotSpotResolvedObjectTypeImpl) {
            HotSpotResolvedObjectTypeImpl hotspotResolved = (HotSpotResolvedObjectTypeImpl) resolved;
            int vtableIndex = getVtableIndex(hotspotResolved);
            return vtableIndex >= 0 && vtableIndex < hotspotResolved.getVtableLength();
        }
        return false;
    }

    private int getVtableIndex(HotSpotResolvedObjectTypeImpl resolved) {
        if (!holder.isLinked()) {
            return config().invalidVtableIndex;
        }
        if (holder.isInterface()) {
            if (resolved.isInterface()) {
                return config().invalidVtableIndex;
            }
            return getVtableIndexForInterfaceMethod(resolved);
        }
        return getVtableIndex();
    }

    /**
     * Returns this method's virtual table index.
     *
     * @return virtual table index
     */
    private int getVtableIndex() {
        assert !holder.isInterface();
        HotSpotVMConfig config = config();
        int result = UNSAFE.getInt(metaspaceMethod + config.methodVtableIndexOffset);
        assert result >= config.nonvirtualVtableIndex : "must be linked";
        return result;
    }

    private int getVtableIndexForInterfaceMethod(ResolvedJavaType resolved) {
        HotSpotResolvedObjectTypeImpl hotspotType = (HotSpotResolvedObjectTypeImpl) resolved;
        return compilerToVM().getVtableIndexForInterfaceMethod(hotspotType, this);
    }

    /**
     * The {@link SpeculationLog} for methods compiled by JVMCI hang off this per-declaring-type
     * {@link ClassValue}. The raw Method* value is safe to use as a key in the map as a) it is
     * never moves and b) we never read from it.
     * <p>
     * One implication is that we will preserve {@link SpeculationLog}s for methods that have been
     * redefined via class redefinition. It's tempting to periodically flush such logs but we cannot
     * read the JVM_ACC_IS_OBSOLETE bit (or anything else) via the raw pointer as obsoleted methods
     * are subject to clean up and deletion (see InstanceKlass::purge_previous_versions_internal).
     */
    private static final ClassValue<Map<Long, SpeculationLog>> SpeculationLogs = new ClassValue<Map<Long, SpeculationLog>>() {
        @Override
        protected Map<Long, SpeculationLog> computeValue(java.lang.Class<?> type) {
            return new HashMap<>(4);
        }
    };

    @Override
    public SpeculationLog getSpeculationLog() {
        Map<Long, SpeculationLog> map = SpeculationLogs.get(holder.mirror());
        synchronized (map) {
            SpeculationLog log = map.get(this.metaspaceMethod);
            if (log == null) {
                log = new HotSpotSpeculationLog();
                map.put(metaspaceMethod, log);
            }
            return log;
        }
    }

    @Override
    public int intrinsicId() {
        HotSpotVMConfig config = config();
        return UNSAFE.getChar(metaspaceMethod + config.methodIntrinsicIdOffset);
    }

    @Override
    public boolean isIntrinsicCandidate() {
        return (getFlags() & config().methodFlagsIntrinsicCandidate) != 0;
    }

    /**
     * Allocates a compile id for this method by asking the VM for one.
     *
     * @param entryBCI entry bci
     * @return compile id
     */
    @Override
    public int allocateCompileId(int entryBCI) {
        return compilerToVM().allocateCompileId(this, entryBCI);
    }

    @Override
    public boolean hasCodeAtLevel(int entryBCI, int level) {
        if (entryBCI == config().invocationEntryBci) {
            return hasCompiledCodeAtLevel(level);
        }
        return compilerToVM().hasCompiledCodeForOSR(this, entryBCI, level);
    }

    public int methodIdnum() {
        return UNSAFE.getChar(getConstMethod() + config().constMethodMethodIdnumOffset);
    }
}
