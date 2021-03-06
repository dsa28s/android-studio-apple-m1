/*
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2019, Red Hat Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"

#include <asm/hwcap.h>
#include <sys/auxv.h>
#include <sys/prctl.h>

#ifndef HWCAP_AES
#define HWCAP_AES   (1<<3)
#endif

#ifndef HWCAP_PMULL
#define HWCAP_PMULL (1<<4)
#endif

#ifndef HWCAP_SHA1
#define HWCAP_SHA1  (1<<5)
#endif

#ifndef HWCAP_SHA2
#define HWCAP_SHA2  (1<<6)
#endif

#ifndef HWCAP_CRC32
#define HWCAP_CRC32 (1<<7)
#endif

#ifndef HWCAP_ATOMICS
#define HWCAP_ATOMICS (1<<8)
#endif

int VM_Version::get_current_sve_vector_length() {
  assert(_features & CPU_SVE, "should not call this");
  return prctl(PR_SVE_GET_VL);
}

int VM_Version::set_and_get_current_sve_vector_lenght(int length) {
  assert(_features & CPU_SVE, "should not call this");
  int new_length = prctl(PR_SVE_SET_VL, length);
  return new_length;
}

void VM_Version::get_os_cpu_info() {

  uint64_t auxv = getauxval(AT_HWCAP);
  uint64_t auxv2 = getauxval(AT_HWCAP2);

  static_assert(CPU_FP      == HWCAP_FP);
  static_assert(CPU_ASIMD   == HWCAP_ASIMD);
  static_assert(CPU_EVTSTRM == HWCAP_EVTSTRM);
  static_assert(CPU_AES     == HWCAP_AES);
  static_assert(CPU_PMULL   == HWCAP_PMULL);
  static_assert(CPU_SHA1    == HWCAP_SHA1);
  static_assert(CPU_SHA2    == HWCAP_SHA2);
  static_assert(CPU_CRC32   == HWCAP_CRC32);
  static_assert(CPU_LSE     == HWCAP_ATOMICS);
  static_assert(CPU_DCPOP   == HWCAP_DCPOP);
  static_assert(CPU_SHA512  == HWCAP_SHA512);
  static_assert(CPU_SVE     == HWCAP_SVE);
  _features = auxv & (
      HWCAP_FP      |
      HWCAP_ASIMD   |
      HWCAP_EVTSTRM |
      HWCAP_AES     |
      HWCAP_PMULL   |
      HWCAP_SHA1    |
      HWCAP_SHA2    |
      HWCAP_CRC32   |
      HWCAP_ATOMICS |
      HWCAP_DCPOP   |
      HWCAP_SHA512  |
      HWCAP_SVE);

  if (auxv2 & HWCAP2_SVE2) _features |= CPU_SVE2;

  uint64_t ctr_el0;
  uint64_t dczid_el0;
  __asm__ (
    "mrs %0, CTR_EL0\n"
    "mrs %1, DCZID_EL0\n"
    : "=r"(ctr_el0), "=r"(dczid_el0)
  );

  _icache_line_size = (1 << (ctr_el0 & 0x0f)) * 4;
  _dcache_line_size = (1 << ((ctr_el0 >> 16) & 0x0f)) * 4;

  if (!(dczid_el0 & 0x10)) {
    _zva_length = 4 << (dczid_el0 & 0xf);
  }

  int cpu_lines = 0;
  if (FILE *f = fopen("/proc/cpuinfo", "r")) {
    // need a large buffer as the flags line may include lots of text
    char buf[1024], *p;
    while (fgets(buf, sizeof (buf), f) != NULL) {
      if ((p = strchr(buf, ':')) != NULL) {
        long v = strtol(p+1, NULL, 0);
        if (strncmp(buf, "CPU implementer", sizeof "CPU implementer" - 1) == 0) {
          _cpu = v;
          cpu_lines++;
        } else if (strncmp(buf, "CPU variant", sizeof "CPU variant" - 1) == 0) {
          _variant = v;
        } else if (strncmp(buf, "CPU part", sizeof "CPU part" - 1) == 0) {
          if (_model != v)  _model2 = _model;
          _model = v;
        } else if (strncmp(buf, "CPU revision", sizeof "CPU revision" - 1) == 0) {
          _revision = v;
        } else if (strncmp(buf, "flags", sizeof("flags") - 1) == 0) {
          if (strstr(p+1, "dcpop")) {
            guarantee(_features & CPU_DCPOP, "dcpop availability should be consistent");
          }
        }
      }
    }
    fclose(f);
  }
  guarantee(cpu_lines == os::processor_count(), "core count should be consistent");
}
