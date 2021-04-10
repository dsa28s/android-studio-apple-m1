/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package test.java.time.chrono;

import java.time.*;
import java.time.chrono.*;
import java.time.format.*;
import java.util.Locale;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

/**
 * Tests Era.getDisplayName() correctly returns the name based on each
 * chrono implementation.
 * Note: The exact result may depend on locale data provider's implementation.
 *
 * @bug 8171049
 */
@Test
public class TestEraDisplayName {
    private static final Locale THAI = Locale.forLanguageTag("th-TH");
    private static final Locale EGYPT = Locale.forLanguageTag("ar-EG");

    @DataProvider(name="eraDisplayName")
    Object[][] eraDisplayName() {
        return new Object[][] {
            // Era, text style, displyay locale, expected name
            // IsoEra
            { IsoEra.BCE,   TextStyle.FULL,     Locale.US,      "Before Christ" },
            { IsoEra.CE,    TextStyle.FULL,     Locale.US,      "Anno Domini" },
            { IsoEra.BCE,   TextStyle.FULL,     Locale.JAPAN,   "\u7d00\u5143\u524d" },
            { IsoEra.CE,    TextStyle.FULL,     Locale.JAPAN,   "\u897f\u66a6" },
            { IsoEra.BCE,   TextStyle.SHORT,    Locale.US,      "BC" },
            { IsoEra.CE,    TextStyle.SHORT,    Locale.US,      "AD" },
            { IsoEra.BCE,   TextStyle.SHORT,    Locale.JAPAN,   "\u7d00\u5143\u524d" },
            { IsoEra.CE,    TextStyle.SHORT,    Locale.JAPAN,   "\u897f\u66a6" },
            { IsoEra.BCE,   TextStyle.NARROW,   Locale.US,      "B" },
            { IsoEra.CE,    TextStyle.NARROW,   Locale.US,      "A" },
            { IsoEra.BCE,   TextStyle.NARROW,   Locale.JAPAN,   "BC" },
            { IsoEra.CE,    TextStyle.NARROW,   Locale.JAPAN,   "AD" },

            // JapaneseEra
            { JapaneseEra.MEIJI,    TextStyle.FULL,     Locale.US,      "Meiji" },
            { JapaneseEra.TAISHO,   TextStyle.FULL,     Locale.US,      "Taisho" },
            { JapaneseEra.SHOWA,    TextStyle.FULL,     Locale.US,      "Showa" },
            { JapaneseEra.HEISEI,   TextStyle.FULL,     Locale.US,      "Heisei" },
            { JapaneseEra.MEIJI,    TextStyle.FULL,     Locale.JAPAN,   "\u660e\u6cbb" },
            { JapaneseEra.TAISHO,   TextStyle.FULL,     Locale.JAPAN,   "\u5927\u6b63" },
            { JapaneseEra.SHOWA,    TextStyle.FULL,     Locale.JAPAN,   "\u662d\u548c" },
            { JapaneseEra.HEISEI,   TextStyle.FULL,     Locale.JAPAN,   "\u5e73\u6210" },
            { JapaneseEra.MEIJI,    TextStyle.SHORT,    Locale.US,      "Meiji" },
            { JapaneseEra.TAISHO,   TextStyle.SHORT,    Locale.US,      "Taisho" },
            { JapaneseEra.SHOWA,    TextStyle.SHORT,    Locale.US,      "Showa" },
            { JapaneseEra.HEISEI,   TextStyle.SHORT,    Locale.US,      "Heisei" },
            { JapaneseEra.MEIJI,    TextStyle.SHORT,    Locale.JAPAN,   "\u660e\u6cbb" },
            { JapaneseEra.TAISHO,   TextStyle.SHORT,    Locale.JAPAN,   "\u5927\u6b63" },
            { JapaneseEra.SHOWA,    TextStyle.SHORT,    Locale.JAPAN,   "\u662d\u548c" },
            { JapaneseEra.HEISEI,   TextStyle.SHORT,    Locale.JAPAN,   "\u5e73\u6210" },
            { JapaneseEra.MEIJI,    TextStyle.NARROW,   Locale.US,      "M" },
            { JapaneseEra.TAISHO,   TextStyle.NARROW,   Locale.US,      "T" },
            { JapaneseEra.SHOWA,    TextStyle.NARROW,   Locale.US,      "S" },
            { JapaneseEra.HEISEI,   TextStyle.NARROW,   Locale.US,      "H" },
            { JapaneseEra.MEIJI,    TextStyle.NARROW,   Locale.JAPAN,   "M" },
            { JapaneseEra.TAISHO,   TextStyle.NARROW,   Locale.JAPAN,   "T" },
            { JapaneseEra.SHOWA,    TextStyle.NARROW,   Locale.JAPAN,   "S" },
            { JapaneseEra.HEISEI,   TextStyle.NARROW,   Locale.JAPAN,   "H" },

            // ThaiBuddhistEra
            { ThaiBuddhistEra.BEFORE_BE,    TextStyle.FULL, Locale.US,      "BC" },
            { ThaiBuddhistEra.BE,           TextStyle.FULL, Locale.US,      "BE" },
            { ThaiBuddhistEra.BEFORE_BE,    TextStyle.FULL, THAI,           "BC" },
            { ThaiBuddhistEra.BE,           TextStyle.FULL, THAI,
                "\u0e1e\u0e38\u0e17\u0e18\u0e28\u0e31\u0e01\u0e23\u0e32\u0e0a" },
            { ThaiBuddhistEra.BEFORE_BE,    TextStyle.SHORT, Locale.US,     "BC" },
            { ThaiBuddhistEra.BE,           TextStyle.SHORT, Locale.US,     "BE" },
            { ThaiBuddhistEra.BEFORE_BE,    TextStyle.SHORT, THAI,
                "\u0e1b\u0e35\u0e01\u0e48\u0e2d\u0e19\u0e04\u0e23\u0e34\u0e2a" +
                "\u0e15\u0e4c\u0e01\u0e32\u0e25\u0e17\u0e35\u0e48" },
            { ThaiBuddhistEra.BE,           TextStyle.SHORT, THAI,  "\u0e1e.\u0e28." },
            { ThaiBuddhistEra.BEFORE_BE,    TextStyle.NARROW, Locale.US,    "BC" },
            { ThaiBuddhistEra.BE,           TextStyle.NARROW, Locale.US,    "BE" },
            { ThaiBuddhistEra.BEFORE_BE,    TextStyle.NARROW, THAI,         "BC" },
            { ThaiBuddhistEra.BE,           TextStyle.NARROW, THAI,         "\u0e1e.\u0e28." },

            // MinguoEra
            { MinguoEra.BEFORE_ROC, TextStyle.FULL,     Locale.US,      "Before R.O.C." },
            { MinguoEra.ROC,        TextStyle.FULL,     Locale.US,      "Minguo" },
            { MinguoEra.BEFORE_ROC, TextStyle.FULL,     Locale.TAIWAN,  "\u6c11\u570b\u524d" },
            { MinguoEra.ROC,        TextStyle.FULL,     Locale.TAIWAN,  "\u6c11\u570b" },
            { MinguoEra.BEFORE_ROC, TextStyle.SHORT,    Locale.US,      "Before R.O.C." },
            { MinguoEra.ROC,        TextStyle.SHORT,    Locale.US,      "Minguo" },
            { MinguoEra.BEFORE_ROC, TextStyle.SHORT,    Locale.TAIWAN,  "\u6c11\u570b\u524d" },
            { MinguoEra.ROC,        TextStyle.SHORT,    Locale.TAIWAN,  "\u6c11\u570b" },
            { MinguoEra.BEFORE_ROC, TextStyle.NARROW,   Locale.US,      "Before R.O.C." },
            { MinguoEra.ROC,        TextStyle.NARROW,   Locale.US,      "Minguo" },
            { MinguoEra.BEFORE_ROC, TextStyle.NARROW,   Locale.TAIWAN,  "\u6c11\u570b\u524d" },
            { MinguoEra.ROC,        TextStyle.NARROW,   Locale.TAIWAN,  "\u6c11\u570b" },

            // HijrahEra
            { HijrahEra.AH, TextStyle.FULL,     Locale.US,  "AH" },
            { HijrahEra.AH, TextStyle.FULL,     EGYPT,      "\u0647\u0640" },
            { HijrahEra.AH, TextStyle.SHORT,    Locale.US,  "AH" },
            { HijrahEra.AH, TextStyle.SHORT,    EGYPT,      "\u0647\u0640" },
            { HijrahEra.AH, TextStyle.NARROW,   Locale.US,  "AH" },
            { HijrahEra.AH, TextStyle.NARROW,   EGYPT,      "\u0647\u0640" },
        };
    }

    @Test(dataProvider="eraDisplayName")
    public void test_eraDisplayName(Era era, TextStyle style, Locale locale, String expected) {
        assertEquals(era.getDisplayName(style, locale), expected);
    }
}
