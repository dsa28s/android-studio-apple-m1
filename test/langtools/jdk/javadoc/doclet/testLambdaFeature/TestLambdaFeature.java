/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8004893 8022738 8029143 8175200 8186332
 * @summary  Make sure that the lambda feature changes work fine in
 *           javadoc.
 * @author   bpatel
 * @library  ../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester TestLambdaFeature
 * @run main TestLambdaFeature
 */

/*
 * NOTE : This test should be elided when version 1.7 support is removed from the JDK
 *              or the negative part of the test showing 1.7's non-support should be
 *              removed [ 8022738 ]
 */

public class TestLambdaFeature extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestLambdaFeature tester = new TestLambdaFeature();
        tester.runTests();
    }

    @Test
    void testDefault() {
        javadoc("-d", "out-default",
                "-sourcepath", testSrc,
                "pkg", "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg/A.html", true,
                "<td class=\"colFirst\"><code>default void</code></td>",
                "<pre class=\"methodSignature\">default&nbsp;void&nbsp;defaultMethod()</pre>",
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>"
                + "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t2\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(2);\">Instance Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" "
                + "class=\"tableTab\"><span><a href=\"javascript:show(4);\">"
                + "Abstract Methods</a></span><span class=\"tabEnd\">&nbsp;</span>"
                + "</span><span id=\"t5\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(16);\">Default Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span></caption>",
                "<dl>\n"
                + "<dt>Functional Interface:</dt>\n"
                + "<dd>This is a functional interface and can therefore be used as "
                + "the assignment target for a lambda expression or method "
                + "reference.</dd>\n"
                + "</dl>");

        checkOutput("pkg1/FuncInf.html", true,
                "<dl>\n"
                + "<dt>Functional Interface:</dt>\n"
                + "<dd>This is a functional interface and can therefore be used as "
                + "the assignment target for a lambda expression or method "
                + "reference.</dd>\n"
                + "</dl>");

        checkOutput("pkg/A.html", false,
                "<td class=\"colFirst\"><code>default default void</code></td>",
                "<pre>default&nbsp;default&nbsp;void&nbsp;defaultMethod()</pre>");

        checkOutput("pkg/B.html", false,
                "<td class=\"colFirst\"><code>default void</code></td>",
                "<dl>\n"
                + "<dt>Functional Interface:</dt>");

        checkOutput("pkg1/NotAFuncInf.html", false,
                "<dl>\n"
                + "<dt>Functional Interface:</dt>\n"
                + "<dd>This is a functional interface and can therefore be used as "
                + "the assignment target for a lambda expression or method "
                + "reference.</dd>\n"
                + "</dl>");
    }

    @Test
    void testSource7() {
        javadoc("-d", "out-7",
                "-sourcepath", testSrc,
                "-source", "1.7",
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/FuncInf.html", false,
                "<dl>\n"
                + "<dt>Functional Interface:</dt>");
    }
}
