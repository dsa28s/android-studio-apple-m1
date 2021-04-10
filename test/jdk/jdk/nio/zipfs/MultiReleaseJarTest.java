/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8144355 8144062 8176709 8194070 8193802
 * @summary Test aliasing additions to ZipFileSystem for multi-release jar files
 * @library /lib/testlibrary/java/util/jar
 * @modules jdk.compiler
 *          jdk.jartool
 *          jdk.zipfs
 * @build Compiler JarBuilder CreateMultiReleaseTestJars
 * @run testng MultiReleaseJarTest
 */

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.Runtime.Version;
import java.net.URI;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.*;

public class MultiReleaseJarTest {
    final private int MAJOR_VERSION = Runtime.version().major();

    final private String userdir = System.getProperty("user.dir",".");
    final private CreateMultiReleaseTestJars creator =  new CreateMultiReleaseTestJars();
    final private Map<String,String> stringEnv = new HashMap<>();
    final private Map<String,Integer> integerEnv = new HashMap<>();
    final private Map<String,Version> versionEnv = new HashMap<>();
    final private String className = "version.Version";
    final private MethodType mt = MethodType.methodType(int.class);

    private String entryName;
    private URI uvuri;
    private URI mruri;
    private URI smruri;

    @BeforeClass
    public void initialize() throws Exception {
        creator.compileEntries();
        creator.buildUnversionedJar();
        creator.buildMultiReleaseJar();
        creator.buildShortMultiReleaseJar();
        String ssp = Paths.get(userdir, "unversioned.jar").toUri().toString();
        uvuri = new URI("jar", ssp , null);
        ssp = Paths.get(userdir, "multi-release.jar").toUri().toString();
        mruri = new URI("jar", ssp, null);
        ssp = Paths.get(userdir, "short-multi-release.jar").toUri().toString();
        smruri = new URI("jar", ssp, null);
        entryName = className.replace('.', '/') + ".class";
    }

    public void close() throws IOException {
        Files.delete(Paths.get(userdir, "unversioned.jar"));
        Files.delete(Paths.get(userdir, "multi-release.jar"));
        Files.delete(Paths.get(userdir, "short-multi-release.jar"));
    }

    @DataProvider(name="strings")
    public Object[][] createStrings() {
        return new Object[][]{
                {"runtime", MAJOR_VERSION},
                {"-20", 8},
                {"0", 8},
                {"8", 8},
                {"9", 9},
                {Integer.toString(MAJOR_VERSION), MAJOR_VERSION},
                {Integer.toString(MAJOR_VERSION+1), MAJOR_VERSION},
                {"50", MAJOR_VERSION}
        };
    }

    @DataProvider(name="integers")
    public Object[][] createIntegers() {
        return new Object[][] {
                {new Integer(-5), 8},
                {new Integer(0), 8},
                {new Integer(8), 8},
                {new Integer(9), 9},
                {new Integer(MAJOR_VERSION), MAJOR_VERSION},
                {new Integer(MAJOR_VERSION + 1), MAJOR_VERSION},
                {new Integer(100), MAJOR_VERSION}
        };
    }

    @DataProvider(name="versions")
    public Object[][] createVersions() {
        return new Object[][] {
                {Version.parse("8"),    8},
                {Version.parse("9"),    9},
                {Version.parse("11"),  MAJOR_VERSION},
                {Version.parse("100"), MAJOR_VERSION}
        };
    }

    // Not the best test but all I can do since ZipFileSystem and JarFileSystem
    // are not public, so I can't use (fs instanceof ...)
    @Test
    public void testNewFileSystem() throws Exception {
        Map<String,String> env = new HashMap<>();
        // no configuration, treat multi-release jar as unversioned
        try (FileSystem fs = FileSystems.newFileSystem(mruri, env)) {
            Assert.assertTrue(readAndCompare(fs, 8));
        }
        env.put("multi-release", "runtime");
        // a configuration and jar file is multi-release
        try (FileSystem fs = FileSystems.newFileSystem(mruri, env)) {
            Assert.assertTrue(readAndCompare(fs, MAJOR_VERSION));
        }
        // a configuration but jar file is unversioned
        try (FileSystem fs = FileSystems.newFileSystem(uvuri, env)) {
            Assert.assertTrue(readAndCompare(fs, 8));
        }
    }

    private boolean readAndCompare(FileSystem fs, int expected) throws IOException {
        Path path = fs.getPath("version/Version.java");
        String src = new String(Files.readAllBytes(path));
        return src.contains("return " + expected);
    }

    @Test(dataProvider="strings")
    public void testStrings(String value, int expected) throws Throwable {
        stringEnv.put("multi-release", value);
        runTest(stringEnv, expected);
    }

    @Test(dataProvider="integers")
    public void testIntegers(Integer value, int expected) throws Throwable {
        integerEnv.put("multi-release", value);
        runTest(integerEnv, expected);
    }

    @Test(dataProvider="versions")
    public void testVersions(Version value, int expected) throws Throwable {
        versionEnv.put("multi-release", value);
        runTest(versionEnv, expected);
    }

    @Test
    public void testShortJar() throws Throwable {
        integerEnv.put("multi-release", Integer.valueOf(MAJOR_VERSION));
        runTest(smruri, integerEnv, MAJOR_VERSION);
        integerEnv.put("multi-release", Integer.valueOf(9));
        runTest(smruri, integerEnv, 8);
    }

    private void runTest(Map<String,?> env, int expected) throws Throwable {
        runTest(mruri, env, expected);
    }

    private void runTest(URI uri, Map<String,?> env, int expected) throws Throwable {
        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Path version = fs.getPath(entryName);
            byte [] bytes = Files.readAllBytes(version);
            Class<?> vcls = (new ByteArrayClassLoader(fs)).defineClass(className, bytes);
            MethodHandle mh = MethodHandles.lookup().findVirtual(vcls, "getVersion", mt);
            Assert.assertEquals((int)mh.invoke(vcls.newInstance()), expected);
        }
    }

    @Test
    public void testIsMultiReleaseJar() throws Exception {
        // Re-examine commented out tests as part of JDK-8176843
        testCustomMultiReleaseValue("true", true);
        testCustomMultiReleaseValue("true\r\nOther: value", true);
        testCustomMultiReleaseValue("true\nOther: value", true);
        //testCustomMultiReleaseValue("true\rOther: value", true);

        testCustomMultiReleaseValue("false", false);
        testCustomMultiReleaseValue(" true", false);
        testCustomMultiReleaseValue("true ", false);
        //testCustomMultiReleaseValue("true\n ", false);
        //testCustomMultiReleaseValue("true\r ", false);
        //testCustomMultiReleaseValue("true\n true", false);
        //testCustomMultiReleaseValue("true\r\n true", false);
    }

    @Test
    public void testMultiReleaseJarWithNonVersionDir() throws Exception {
        String jfname = "multi-release-non-ver.jar";
        Path jfpath = Paths.get(jfname);
        URI uri = new URI("jar", jfpath.toUri().toString() , null);
        JarBuilder jb = new JarBuilder(jfname);
        jb.addAttribute("Multi-Release", "true");
        jb.build();
        Map<String,String> env = Map.of("multi-release", "runtime");
        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Assert.assertTrue(true);
        }
        Files.delete(jfpath);
    }

    private static final AtomicInteger JAR_COUNT = new AtomicInteger(0);

    private void testCustomMultiReleaseValue(String value, boolean expected)
            throws Exception {
        String fileName = "custom-mr" + JAR_COUNT.incrementAndGet() + ".jar";
        creator.buildCustomMultiReleaseJar(fileName, value, Map.of(),
                /*addEntries*/true);

        Map<String,String> env = Map.of("multi-release", "runtime");
        Path filePath = Paths.get(userdir, fileName);
        String ssp = filePath.toUri().toString();
        URI customJar = new URI("jar", ssp , null);
        try (FileSystem fs = FileSystems.newFileSystem(customJar, env)) {
            if (expected) {
                Assert.assertTrue(readAndCompare(fs, MAJOR_VERSION));
            } else {
                Assert.assertTrue(readAndCompare(fs, 8));
            }
        }
        Files.delete(filePath);
    }

    private static class ByteArrayClassLoader extends ClassLoader {
        final private FileSystem fs;

        ByteArrayClassLoader(FileSystem fs) {
            super(null);
            this.fs = fs;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            try {
                return super.loadClass(name);
            } catch (ClassNotFoundException x) {}
            Path cls = fs.getPath(name.replace('.', '/') + ".class");
            try {
                byte[] bytes = Files.readAllBytes(cls);
                return defineClass(name, bytes);
            } catch (IOException x) {
                throw new ClassNotFoundException(x.getMessage());
            }
        }

        public Class<?> defineClass(String name, byte[] bytes) throws ClassNotFoundException {
            if (bytes == null) throw new ClassNotFoundException("No bytes for " + name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
