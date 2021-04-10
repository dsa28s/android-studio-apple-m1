/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     7088989 8000415
 * @summary Ensure the various message digests works correctly
 * @key randomness
 */

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class TestDigest extends UcryptoTest {

    private static final String[] MD_ALGOS = {
        "MD5",
        "SHA",
        "SHA-224",
        "SHA-256",
        "SHA-384",
        "SHA-512",
        "SHA3-224",
        "SHA3-256",
        "SHA3-384",
        "SHA3-512"
    };

    public static void main(String[] args) throws Exception {
        main(new TestDigest(), null);
    }

    public void doTest(Provider p) throws Exception {
        boolean testPassed = true;
        byte[] msg = new byte[200];
        (new SecureRandom()).nextBytes(msg);
        String interopProvName = "SUN";

        MessageDigest md, md2;

        for (String a : MD_ALGOS) {
            System.out.println("Testing " + a);
            try {
                md = MessageDigest.getInstance(a, p);
            } catch (NoSuchAlgorithmException nsae) {
                System.out.println("=> Skip, unsupported");
                continue;
            }
            try {
                md2 = MessageDigest.getInstance(a, interopProvName);
            } catch (NoSuchAlgorithmException nsae) {
                System.out.println("=> Skip, no interop provider found");
                continue;
            }

            // Test Interoperability for update+digest calls
            for (int i = 0; i < 3; i++) {
                md.update(msg);
                byte[] digest = md.digest();
                md2.update(msg);
                byte[] digest2 = md2.digest();
                if (!Arrays.equals(digest, digest2)) {
                    System.out.println("DIFF1 FAILED at iter " + i);
                    testPassed = false;
                } else {
                    System.out.println("...diff1 test passed");
                }
            }

            // Test Interoperability for digest calls
            md = MessageDigest.getInstance(a, p);
            md2 = MessageDigest.getInstance(a, interopProvName);

            for (int i = 0; i < 3; i++) {
                byte[] digest = md.digest();
                byte[] digest2 = md2.digest();
                if (!Arrays.equals(digest, digest2)) {
                    System.out.println("DIFF2 FAILED at iter " + i);
                    testPassed = false;
                } else {
                    System.out.println("...diff2 test passed");
                }
            }

            // Test Cloning functionality if supported
            md = MessageDigest.getInstance(a, p);
            try {
                md2 = (MessageDigest) md.clone(); // clone right after construction
            } catch (CloneNotSupportedException cnse) {
                System.out.println("...no clone support");
                continue;
            }
            byte[] digest = md.digest();
            byte[] digest2 = md2.digest();
            if (!Arrays.equals(digest, digest2)) {
                System.out.println("DIFF-3.1 FAILED");
                testPassed = false;
            } else {
                System.out.println("...diff3.1 tests passed");
            }
            md.update(msg);
            md2 = (MessageDigest) md.clone(); // clone again after update call
            digest = md.digest();
            digest2 = md2.digest();
            if (!Arrays.equals(digest, digest2)) {
                System.out.println("DIFF-3.2 FAILED");
                testPassed = false;
            } else {
                System.out.println("...diff3.2 tests passed");
            }
            md2 = (MessageDigest) md.clone(); // clone after digest
            digest = md.digest();
            digest2 = md2.digest();
            if (!Arrays.equals(digest, digest2)) {
                System.out.println("DIFF-3.3 FAILED");
                testPassed = false;
            } else {
                System.out.println("...diff3.3 tests passed");
            }
        }
        if (!testPassed) {
            throw new RuntimeException("One or more MD test failed!");
        } else {
            System.out.println("MD Tests Passed");
        }
    }
}
