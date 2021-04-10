/*
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ARM_VM_JNITYPES_ARM_HPP
#define CPU_ARM_VM_JNITYPES_ARM_HPP

#include "jni.h"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"

// This file holds platform-dependent routines used to write primitive jni
// types to the array of arguments passed into JavaCalls::call

class JNITypes : AllStatic {
  // These functions write a java primitive type (in native format)
  // to a java stack slot array to be passed as an argument to JavaCalls:calls.
  // I.e., they are functionally 'push' operations if they have a 'pos'
  // formal parameter.  Note that jlong's and jdouble's are written
  // _in reverse_ of the order in which they appear in the interpreter
  // stack.  This is because call stubs (see stubGenerator_arm.cpp)
  // reverse the argument list constructed by JavaCallArguments (see
  // javaCalls.hpp).

private:

#ifndef AARCH64
  // 32bit Helper routines.
  static inline void put_int2r(jint *from, intptr_t *to)           { *(jint *)(to++) = from[1];
                                                                        *(jint *)(to  ) = from[0]; }
  static inline void put_int2r(jint *from, intptr_t *to, int& pos) { put_int2r(from, to + pos); pos += 2; }
#endif

public:
  // Ints are stored in native format in one JavaCallArgument slot at *to.
  static inline void put_int(jint  from, intptr_t *to)           { *(jint *)(to +   0  ) =  from; }
  static inline void put_int(jint  from, intptr_t *to, int& pos) { *(jint *)(to + pos++) =  from; }
  static inline void put_int(jint *from, intptr_t *to, int& pos) { *(jint *)(to + pos++) = *from; }

#ifdef AARCH64
  // Longs are stored in native format in one JavaCallArgument slot at *(to+1).
  static inline void put_long(jlong  from, intptr_t *to)           { *(jlong *)(to + 1 +   0) =  from; }
  static inline void put_long(jlong  from, intptr_t *to, int& pos) { *(jlong *)(to + 1 + pos) =  from; pos += 2; }
  static inline void put_long(jlong *from, intptr_t *to, int& pos) { *(jlong *)(to + 1 + pos) = *from; pos += 2; }
#else
  // Longs are stored in big-endian word format in two JavaCallArgument slots at *to.
  // The high half is in *to and the low half in *(to+1).
  static inline void put_long(jlong  from, intptr_t *to)           { put_int2r((jint *)&from, to); }
  static inline void put_long(jlong  from, intptr_t *to, int& pos) { put_int2r((jint *)&from, to, pos); }
  static inline void put_long(jlong *from, intptr_t *to, int& pos) { put_int2r((jint *) from, to, pos); }
#endif

  // Oops are stored in native format in one JavaCallArgument slot at *to.
  static inline void put_obj(oop  from, intptr_t *to)           { *(oop *)(to +   0  ) =  from; }
  static inline void put_obj(oop  from, intptr_t *to, int& pos) { *(oop *)(to + pos++) =  from; }
  static inline void put_obj(oop *from, intptr_t *to, int& pos) { *(oop *)(to + pos++) = *from; }

  // Floats are stored in native format in one JavaCallArgument slot at *to.
  static inline void put_float(jfloat  from, intptr_t *to)           { *(jfloat *)(to +   0  ) =  from;  }
  static inline void put_float(jfloat  from, intptr_t *to, int& pos) { *(jfloat *)(to + pos++) =  from; }
  static inline void put_float(jfloat *from, intptr_t *to, int& pos) { *(jfloat *)(to + pos++) = *from; }

#ifdef AARCH64
  // Doubles are stored in native word format in one JavaCallArgument slot at *(to+1).
  static inline void put_double(jdouble  from, intptr_t *to)           { *(jdouble *)(to + 1 +   0) =  from; }
  static inline void put_double(jdouble  from, intptr_t *to, int& pos) { *(jdouble *)(to + 1 + pos) =  from; pos += 2; }
  static inline void put_double(jdouble *from, intptr_t *to, int& pos) { *(jdouble *)(to + 1 + pos) = *from; pos += 2; }
#else
  // Doubles are stored in big-endian word format in two JavaCallArgument slots at *to.
  // The high half is in *to and the low half in *(to+1).
  static inline void put_double(jdouble  from, intptr_t *to)           { put_int2r((jint *)&from, to); }
  static inline void put_double(jdouble  from, intptr_t *to, int& pos) { put_int2r((jint *)&from, to, pos); }
  static inline void put_double(jdouble *from, intptr_t *to, int& pos) { put_int2r((jint *) from, to, pos); }
#endif

};

#endif // CPU_ARM_VM_JNITYPES_ARM_HPP
