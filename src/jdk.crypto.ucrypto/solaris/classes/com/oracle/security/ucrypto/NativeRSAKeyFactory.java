/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.security.ucrypto;

import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListSet;
import java.lang.ref.*;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Key;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.KeyFactorySpi;

import java.security.spec.*;

/**
 * Ucrypto-private KeyFactory class for generating native keys
 * needed for using ucrypto APIs.
 *
 * @since 9
 */
public final class NativeRSAKeyFactory extends KeyFactorySpi {

    @Override
    protected PrivateKey engineGeneratePrivate(KeySpec keySpec)
        throws InvalidKeySpecException {
        if (keySpec instanceof RSAPrivateCrtKeySpec) {
            return new NativeKey.RSAPrivateCrt(keySpec);
        } else if (keySpec instanceof RSAPrivateKeySpec) {
            return new NativeKey.RSAPrivate(keySpec);
        } else {
            throw new InvalidKeySpecException("Unsupported key spec." +
                " Received: " + keySpec.getClass().getName());
        }
    }

    @Override
    protected PublicKey engineGeneratePublic(KeySpec keySpec)
        throws InvalidKeySpecException {
        return new NativeKey.RSAPublic(keySpec);
    }

    @Override
    protected <T extends KeySpec> T
        engineGetKeySpec(Key key, Class<T> keySpec)
            throws InvalidKeySpecException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Key engineTranslateKey(Key key) throws InvalidKeyException {
        // no need to support this
        throw new UnsupportedOperationException();
    }
}
