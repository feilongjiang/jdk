/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Institute of Software, Chinese Academy of Sciences. All rights reserved.
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
 *
 */


/*
 * @test
 * @enablePreview
 * @requires os.arch == "riscv64"
 *
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestRISCV64Extra
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;


import static org.testng.Assert.assertEquals;


public class TestRISCV64Extra {
    static {
        System.loadLibrary("TestRISCV64Extra");
    }

    static final int INT_VALUE = 59;
    static final long LONG_VALUE = 133L;
    static final float FLOAT_VALUE = 32.2f;
    static final double DOUBLE_VALUE = 721.5;
    static Linker LINKER = Linker.nativeLinker();

    @Test(dataProvider = "structs")
    void test(MemoryLayout struct, String fName, BiFunction<MemorySession, MemoryLayout, MemorySegment> initializer,
              Consumer<MemorySegment> checker) throws Throwable {
        var fd = FunctionDescriptor.of(struct, struct);
        var target = SymbolLookup.loaderLookup().lookup(fName).get();
        var mh = LINKER.downcallHandle(target, fd);

        try (MemorySession session = MemorySession.openConfined()) {
            MemorySegment segment = initializer.apply(session, struct);
            MemorySegment returnValue = (MemorySegment) mh.invoke(session, segment);
            checker.accept(returnValue);
        }
    }

    @DataProvider
    public static Object[][] structs() {
        return new Object[][]{
            /* struct{float[2]} */
            {
                MemoryLayout.structLayout(MemoryLayout.sequenceLayout(2, NativeTestHelper.C_FLOAT)),
                "f_s_af2",
                (BiFunction<MemorySession, MemoryLayout, MemorySegment>) (session, layout) -> {
                    MemorySegment segment = session.allocate(layout);
                    segment.set(NativeTestHelper.C_FLOAT, 0, FLOAT_VALUE);
                    segment.set(NativeTestHelper.C_FLOAT, 4, FLOAT_VALUE);
                    return segment;
                },
                (Consumer<MemorySegment>) segment -> {
                    assertEquals(segment.get(NativeTestHelper.C_FLOAT, 0), FLOAT_VALUE);
                    assertEquals(segment.get(NativeTestHelper.C_FLOAT, 4), FLOAT_VALUE);
                }
            },
            /* struct{double[2]} */
            {
                MemoryLayout.structLayout(MemoryLayout.sequenceLayout(2, NativeTestHelper.C_DOUBLE)),
                "f_s_ad2",
                (BiFunction<MemorySession, MemoryLayout, MemorySegment>) (session, layout) -> {
                    MemorySegment segment = session.allocate(layout);
                    segment.set(NativeTestHelper.C_DOUBLE, 0, DOUBLE_VALUE);
                    segment.set(NativeTestHelper.C_DOUBLE, 8, DOUBLE_VALUE);
                    return segment;
                },
                (Consumer<MemorySegment>) segment -> {
                    assertEquals(segment.get(NativeTestHelper.C_DOUBLE, 0), DOUBLE_VALUE);
                    assertEquals(segment.get(NativeTestHelper.C_DOUBLE, 8), DOUBLE_VALUE);
                }
            },
            /* struct{int[1], padding 4B ,double[1]} */
            {
                MemoryLayout.structLayout(MemoryLayout.sequenceLayout(1, NativeTestHelper.C_INT),
                                          MemoryLayout.paddingLayout(32),
                                          MemoryLayout.sequenceLayout(1, NativeTestHelper.C_DOUBLE)),
                "f_s_ai1ad1",
                (BiFunction<MemorySession, MemoryLayout, MemorySegment>) (session, layout) -> {
                    MemorySegment segment = session.allocate(layout);
                    segment.set(NativeTestHelper.C_INT, 0, INT_VALUE);
                    segment.set(NativeTestHelper.C_DOUBLE, 8, DOUBLE_VALUE);
                    return segment;
                },
                (Consumer<MemorySegment>) segment -> {
                    assertEquals(segment.get(NativeTestHelper.C_INT, 0), INT_VALUE);
                    assertEquals(segment.get(NativeTestHelper.C_DOUBLE, 8), DOUBLE_VALUE);
                }
            },
            /* struct{struct{int, float}} */
            {
                MemoryLayout.structLayout(MemoryLayout.structLayout(NativeTestHelper.C_LONG_LONG,
                                                                    NativeTestHelper.C_FLOAT)),
                "f_s_s_lf",
                (BiFunction<MemorySession, MemoryLayout, MemorySegment>) (session, layout) -> {
                    MemorySegment segment = session.allocate(layout);
                    segment.set(NativeTestHelper.C_LONG_LONG, 0, LONG_VALUE);
                    segment.set(NativeTestHelper.C_FLOAT, 8, FLOAT_VALUE);
                    return segment;
                },
                (Consumer<MemorySegment>) segment -> {
                    assertEquals(segment.get(NativeTestHelper.C_LONG_LONG, 0), LONG_VALUE);
                    assertEquals(segment.get(NativeTestHelper.C_FLOAT, 8), FLOAT_VALUE);
                }
            }
        };
    }

}