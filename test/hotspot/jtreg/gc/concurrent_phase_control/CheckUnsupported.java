/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package gc.concurrent_phase_control;

/*
 * Utility class that provides verification of expected behavior of
 * the Concurrent GC Phase Control WB API when the current GC does not
 * support phase control.  The invoking test must provide WhiteBox access.
 */

import sun.hotspot.WhiteBox;

public class CheckUnsupported {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void check(String gcName) throws Exception {
        // Verify unsupported.
        if (WB.supportsConcurrentGCPhaseControl()) {
            throw new RuntimeException(
                gcName + " unexpectedly supports phase control");
        }

        // Verify phase sequence is empty.
        String[] phases = WB.getConcurrentGCPhases();
        if (phases.length > 0) {
            throw new RuntimeException(
                gcName + " unexpectedly has non-empty phases");
        }

        // Verify IllegalStateException thrown by request attempt.
        boolean illegalStateThrown = false;
        try {
            WB.requestConcurrentGCPhase("UNKNOWN PHASE");
        } catch (IllegalStateException e) {
            // Expected.
            illegalStateThrown = true;
        } catch (Exception e) {
            throw new RuntimeException(
                gcName + ": Unexpected exception when requesting phase: "
                + e.toString());
        }
        if (!illegalStateThrown) {
            throw new RuntimeException(
                gcName + ": No exception when requesting phase");
        }
    }
}

