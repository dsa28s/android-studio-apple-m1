/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 */


package org.graalvm.compiler.jtt.threads;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.jtt.hotspot.NotOnDebug;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

public class Monitor_notowner01 extends JTTTest {

    @Rule public TestRule timeout = NotOnDebug.create(Timeout.seconds(20));

    static Object monitor = new Object();
    static Object finished = new Object();

    public static boolean test() throws InterruptedException {
        final BadRunnable badRunnable = new BadRunnable();
        synchronized (monitor) {
            new Thread(badRunnable).start();
            synchronized (finished) {
                finished.wait(1000);
            }
        }
        return badRunnable.caught;
    }

    static class BadRunnable implements Runnable {

        protected boolean caught = false;

        @Override
        public void run() {
            try {
                // we don't own this!
                monitor.wait();
            } catch (InterruptedException ex) {

            } catch (IllegalMonitorStateException ex) {
                caught = true;
                synchronized (finished) {
                    finished.notifyAll();
                }
            }
        }
    }

    @Test
    public void run0() throws Throwable {
        initializeForTimeout();
        runTest("test");
    }

}
