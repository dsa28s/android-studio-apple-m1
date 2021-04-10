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
package jdk.tools.jaotc.test.collect;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Utils {
    public static <T> Set<T> set(T... entries) {
        Set<T> set = new HashSet<T>();
        for (T entry : entries) {
            set.add(entry);
        }
        return set;
    }

    public static String mkpath(String path) {
        return getpath(path).toString();
    }

    public static Set<String> mkpaths(String... paths) {
        Set<String> set = new HashSet<String>();
        for (String entry : paths) {
            set.add(mkpath(entry));
        }
        return set;
    }

    public static Path getpath(String path) {
        if (path.startsWith("/") && System.getProperty("os.name").startsWith("Windows")) {
            path = new File(path).getAbsolutePath();
        }
        return Paths.get(path);
    }
}
