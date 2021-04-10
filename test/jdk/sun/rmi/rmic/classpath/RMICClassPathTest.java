/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyright (c) 2012 IBM Corporation
 */

/* @test
 * @bug 6610897 8030844
 * @summary New constructor in sun.tools.java.ClassPath builds a path using
 *          File.separator instead of File.pathSeparator. This test does
 *          not affect VM global state, so othervm is not required.
 * @modules jdk.rmic/sun.rmi.rmic
 *          jdk.rmic/sun.tools.java
 * @run main RMICClassPathTest
 */

import java.io.File;

import sun.rmi.rmic.BatchEnvironment;

public class RMICClassPathTest {
    private final static String SRC_PATH = System.getProperty("test.src", ".");
    public static void main(String[] args) throws Exception {
        String sysPath = SRC_PATH + "/jdk/jre/lib/rt.jar";
        String clPath = SRC_PATH + "/user.jar" + File.pathSeparator +
            SRC_PATH + "/user2.jar" + File.pathSeparator +
            SRC_PATH + "/user3.jar";

        String cpStr = BatchEnvironment.createClassPath(clPath, sysPath).toString();

        String[] paths = cpStr.split(File.pathSeparator);

        if (paths.length != 4) {
            throw new Exception("ClassPath length is not correct: the expected length is 4 and the actual length is " + paths.length);
        }
    }
}
