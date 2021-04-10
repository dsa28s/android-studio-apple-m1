/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.amd64;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.spi.ArrayOffsetProvider;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Substitutions for {@link java.lang.String} methods.
 */
@ClassSubstitution(String.class)
public class AMD64StringSubstitutions {

    @Fold
    static int charArrayBaseOffset(@InjectedParameter ArrayOffsetProvider arrayOffsetProvider) {
        return arrayOffsetProvider.arrayBaseOffset(JavaKind.Char);
    }

    @Fold
    static int charArrayIndexScale(@InjectedParameter ArrayOffsetProvider arrayOffsetProvider) {
        return arrayOffsetProvider.arrayScalingFactor(JavaKind.Char);
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final ArrayOffsetProvider INJECTED = null;

    // Only exists in JDK <= 8
    @MethodSubstitution(isStatic = true, optional = true)
    public static int indexOf(char[] source, int sourceOffset, int sourceCount,
                    @ConstantNodeParameter char[] target, int targetOffset, int targetCount,
                    int origFromIndex) {
        int fromIndex = origFromIndex;
        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            // The empty string is in every string.
            return fromIndex;
        }

        int totalOffset = sourceOffset + fromIndex;
        if (sourceCount - fromIndex < targetCount) {
            // The empty string contains nothing except the empty string.
            return -1;
        }
        assert sourceCount - fromIndex > 0 && targetCount > 0;

        Pointer sourcePointer = Word.objectToTrackedPointer(source).add(charArrayBaseOffset(INJECTED)).add(totalOffset * charArrayIndexScale(INJECTED));
        Pointer targetPointer = Word.objectToTrackedPointer(target).add(charArrayBaseOffset(INJECTED)).add(targetOffset * charArrayIndexScale(INJECTED));
        int result = AMD64StringIndexOfNode.optimizedStringIndexPointer(sourcePointer, sourceCount - fromIndex, targetPointer, targetCount);
        if (result >= 0) {
            return result + totalOffset;
        }
        return result;
    }

    @MethodSubstitution(isStatic = false)
    @SuppressFBWarnings(value = "ES_COMPARING_PARAMETER_STRING_WITH_EQ", justification = "reference equality on the receiver is what we want")
    public static int compareTo(String receiver, String anotherString) {
        if (receiver == anotherString) {
            return 0;
        }
        char[] value = StringSubstitutions.getValue(receiver);
        char[] other = StringSubstitutions.getValue(anotherString);
        return ArrayCompareToNode.compareTo(value, other, value.length << 1, other.length << 1, JavaKind.Char, JavaKind.Char);
    }

}
