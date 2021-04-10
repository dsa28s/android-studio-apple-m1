/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8195795 8201396 8196202
 * @summary test the use of module directories in output,
 *          and the --no-module-directories option
 * @modules jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @library ../lib /tools/lib
 * @build toolbox.ToolBox toolbox.ModuleBuilder JavadocTester
 * @run main TestModuleDirs
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.ModuleBuilder;
import toolbox.ToolBox;

public class TestModuleDirs extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        TestModuleDirs tester = new TestModuleDirs();
        tester.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    public TestModuleDirs() {
        tb = new ToolBox();
    }

    @Test
    public void testNoModules(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; public class C { }");

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "-quiet",
                "p");

        checkExit(Exit.OK);
        checkFiles(true, "p/package-summary.html");
    }

    @Test
    public void testNoModuleDirs(Path base) throws IOException {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "ma")
                .classes("package pa; public class A {}")
                .exports("pa")
                .write(src);
        new ModuleBuilder(tb, "mb")
                .classes("package pb; public class B {}")
                .exports("pb")
                .write(src);

        javadoc("-d", base.resolve("api").toString(),
                "-quiet",
                "--frames",
                "--module-source-path", src.toString(),
                "--no-module-directories",
                "--module", "ma,mb");

        checkExit(Exit.OK);
        checkFiles(true,
                "ma-frame.html",
                "ma-summary.html",
                "pa/package-summary.html");
        checkFiles(false,
                "ma/module-frame.html",
                "ma/module-summary.html",
                "ma/pa/package-summary.html");
        checkOutput("ma-frame.html", true,
                "<ul>\n"
                + "<li><a href=\"allclasses-frame.html\" target=\"packageFrame\">All&nbsp;Classes</a></li>\n"
                + "<li><a href=\"overview-frame.html\" target=\"packageListFrame\">All&nbsp;Packages</a></li>\n"
                + "<li><a href=\"module-overview-frame.html\" target=\"packageListFrame\">All&nbsp;Modules</a></li>\n"
                + "</ul>\n");
        checkOutput("ma-summary.html", true,
                "<ul class=\"navList\" id=\"allclasses_navbar_top\">\n"
                + "<li><a href=\"allclasses-noframe.html\">All&nbsp;Classes</a></li>\n"
                + "</ul>\n");
        checkOutput("pa/package-summary.html", true,
                "<li><a href=\"../deprecated-list.html\">Deprecated</a></li>\n"
                + "<li><a href=\"../index-all.html\">Index</a></li>");

    }

    @Test
    public void testModuleDirs(Path base) throws IOException {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "ma")
                .classes("package pa; public class A {}")
                .exports("pa")
                .write(src);
        new ModuleBuilder(tb, "mb")
                .classes("package pb; public class B {}")
                .exports("pb")
                .write(src);

        javadoc("-d", base.resolve("api").toString(),
                "-quiet",
                "--frames",
                "--module-source-path", src.toString(),
                "--module", "ma,mb");

        checkExit(Exit.OK);
        checkFiles(false,
                "ma-frame.html",
                "ma-summary.html",
                "pa/package-summary.html");
        checkFiles(true,
                "ma/module-frame.html",
                "ma/module-summary.html",
                "ma/pa/package-summary.html");
        checkOutput("ma/module-frame.html", true,
                "<ul>\n"
                + "<li><a href=\"../allclasses-frame.html\" target=\"packageFrame\">All&nbsp;Classes</a></li>\n"
                + "<li><a href=\"../overview-frame.html\" target=\"packageListFrame\">All&nbsp;Packages</a></li>\n"
                + "<li><a href=\"../module-overview-frame.html\" target=\"packageListFrame\">All&nbsp;Modules</a></li>\n"
                + "</ul>\n");
        checkOutput("ma/module-summary.html", true,
                "<ul class=\"navList\" id=\"allclasses_navbar_top\">\n"
                + "<li><a href=\"../allclasses-noframe.html\">All&nbsp;Classes</a></li>\n"
                + "</ul>\n");
        checkOutput("ma/pa/package-summary.html", true,
                "<li><a href=\"../../deprecated-list.html\">Deprecated</a></li>\n"
                + "<li><a href=\"../../index-all.html\">Index</a></li>");
    }
}

