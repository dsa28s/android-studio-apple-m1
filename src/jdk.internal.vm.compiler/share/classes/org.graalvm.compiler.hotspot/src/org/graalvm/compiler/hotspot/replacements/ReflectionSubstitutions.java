/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.jvmAccWrittenFlags;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassAccessFlagsOffset;

import java.lang.reflect.Modifier;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.hotspot.word.KlassPointer;

/**
 * Substitutions for {@link sun.reflect.Reflection} methods.
 */
@ClassSubstitution(className = {"jdk.internal.reflect.Reflection", "sun.reflect.Reflection"}, optional = true)
public class ReflectionSubstitutions {

    @MethodSubstitution
    public static int getClassAccessFlags(Class<?> aClass) {
        KlassPointer klass = ClassGetHubNode.readClass(GraalDirectives.guardingNonNull(aClass));
        if (klass.isNull()) {
            // Class for primitive type
            return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
        } else {
            return klass.readInt(klassAccessFlagsOffset(INJECTED_VMCONFIG), KLASS_ACCESS_FLAGS_LOCATION) & jvmAccWrittenFlags(INJECTED_VMCONFIG);
        }
    }
}
