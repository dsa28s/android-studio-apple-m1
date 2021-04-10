/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * An option for a configurable file name that can also open a {@link PrintStream} on the file. If
 * no value is given for the option, the stream will output to HotSpot's
 * {@link HotSpotJVMCIRuntime#getLogStream() log} stream
 */
public class PrintStreamOptionKey extends OptionKey<String> {

    public PrintStreamOptionKey() {
        super(null);
    }

    /**
     * @return {@code nameTemplate} with all instances of %p replaced by
     *         {@link GraalServices#getExecutionID()} and %t by {@link System#currentTimeMillis()}
     */
    private static String makeFilename(String nameTemplate) {
        String name = nameTemplate;
        if (name.contains("%p")) {
            name = name.replaceAll("%p", GraalServices.getExecutionID());
        }
        if (name.contains("%t")) {
            name = name.replaceAll("%t", String.valueOf(System.currentTimeMillis()));
        }
        return name;
    }

    /**
     * An output stream that redirects to {@link HotSpotJVMCIRuntime#getLogStream()}. The
     * {@link HotSpotJVMCIRuntime#getLogStream()} value is only accessed the first time an IO
     * operation is performed on the stream. This is required to break a deadlock in early JVMCI
     * initialization.
     */
    static class DelayedOutputStream extends OutputStream {
        private volatile OutputStream lazy;

        private OutputStream lazy() {
            if (lazy == null) {
                synchronized (this) {
                    if (lazy == null) {
                        lazy = HotSpotJVMCIRuntime.runtime().getLogStream();
                    }
                }
            }
            return lazy;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            lazy().write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            lazy().write(b);
        }

        @Override
        public void flush() throws IOException {
            lazy().flush();
        }

        @Override
        public void close() throws IOException {
            lazy().close();
        }
    }

    /**
     * Gets the print stream configured by this option. If no file is configured, the print stream
     * will output to HotSpot's {@link HotSpotJVMCIRuntime#getLogStream() log} stream.
     */
    public PrintStream getStream(OptionValues options) {
        String nameTemplate = getValue(options);
        if (nameTemplate != null) {
            String name = makeFilename(nameTemplate);
            try {
                final boolean enableAutoflush = true;
                PrintStream ps = new PrintStream(new FileOutputStream(name), enableAutoflush);
                /*
                 * Add the JVM and Java arguments to the log file to help identity it.
                 */
                List<String> inputArguments = GraalServices.getInputArguments();
                if (inputArguments != null) {
                    ps.println("VM Arguments: " + String.join(" ", inputArguments));
                }
                String cmd = System.getProperty("sun.java.command");
                if (cmd != null) {
                    ps.println("sun.java.command=" + cmd);
                }
                return ps;
            } catch (FileNotFoundException e) {
                throw new RuntimeException("couldn't open file: " + name, e);
            }
        } else {
            return new PrintStream(new DelayedOutputStream());
        }
    }
}
