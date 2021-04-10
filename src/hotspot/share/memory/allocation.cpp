/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "runtime/threadCritical.hpp"
#include "services/memTracker.hpp"
#include "utilities/ostream.hpp"

// allocate using malloc; will fail if no memory available
char* AllocateHeap(size_t size,
                   MEMFLAGS flags,
                   const NativeCallStack& stack,
                   AllocFailType alloc_failmode /* = AllocFailStrategy::EXIT_OOM*/) {
  char* p = (char*) os::malloc(size, flags, stack);
  if (p == NULL && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "AllocateHeap");
  }
  return p;
}

char* AllocateHeap(size_t size,
                   MEMFLAGS flags,
                   AllocFailType alloc_failmode /* = AllocFailStrategy::EXIT_OOM*/) {
  return AllocateHeap(size, flags, CALLER_PC);
}

char* ReallocateHeap(char *old,
                     size_t size,
                     MEMFLAGS flag,
                     AllocFailType alloc_failmode) {
  char* p = (char*) os::realloc(old, size, flag, CALLER_PC);
  if (p == NULL && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "ReallocateHeap");
  }
  return p;
}

void FreeHeap(void* p) {
  os::free(p);
}

void* MetaspaceObj::_shared_metaspace_base = NULL;
void* MetaspaceObj::_shared_metaspace_top  = NULL;

void* StackObj::operator new(size_t size)     throw() { ShouldNotCallThis(); return 0; }
void  StackObj::operator delete(void* p)              { ShouldNotCallThis(); }
void* StackObj::operator new [](size_t size)  throw() { ShouldNotCallThis(); return 0; }
void  StackObj::operator delete [](void* p)           { ShouldNotCallThis(); }

void* MetaspaceObj::operator new(size_t size, ClassLoaderData* loader_data,
                                 size_t word_size,
                                 MetaspaceObj::Type type, TRAPS) throw() {
  // Klass has it's own operator new
  return Metaspace::allocate(loader_data, word_size, type, THREAD);
}

bool MetaspaceObj::is_valid(const MetaspaceObj* p) {
  // Weed out obvious bogus values first without traversing metaspace
  if ((size_t)p < os::min_page_size()) {
    return false;
  } else if (!is_aligned((address)p, sizeof(MetaWord))) {
    return false;
  }
  return Metaspace::contains((void*)p);
}

void MetaspaceObj::print_address_on(outputStream* st) const {
  st->print(" {" INTPTR_FORMAT "}", p2i(this));
}

void* ResourceObj::operator new(size_t size, Arena *arena) throw() {
  address res = (address)arena->Amalloc(size);
  DEBUG_ONLY(set_allocation_type(res, ARENA);)
  return res;
}

void* ResourceObj::operator new [](size_t size, Arena *arena) throw() {
  address res = (address)arena->Amalloc(size);
  DEBUG_ONLY(set_allocation_type(res, ARENA);)
  return res;
}

void* ResourceObj::operator new(size_t size, allocation_type type, MEMFLAGS flags) throw() {
  address res = NULL;
  switch (type) {
   case C_HEAP:
    res = (address)AllocateHeap(size, flags, CALLER_PC);
    DEBUG_ONLY(set_allocation_type(res, C_HEAP);)
    break;
   case RESOURCE_AREA:
    // new(size) sets allocation type RESOURCE_AREA.
    res = (address)operator new(size);
    break;
   default:
    ShouldNotReachHere();
  }
  return res;
}

void* ResourceObj::operator new [](size_t size, allocation_type type, MEMFLAGS flags) throw() {
  return (address) operator new(size, type, flags);
}

void* ResourceObj::operator new(size_t size, const std::nothrow_t&  nothrow_constant,
    allocation_type type, MEMFLAGS flags) throw() {
  // should only call this with std::nothrow, use other operator new() otherwise
  address res = NULL;
  switch (type) {
   case C_HEAP:
    res = (address)AllocateHeap(size, flags, CALLER_PC, AllocFailStrategy::RETURN_NULL);
    DEBUG_ONLY(if (res!= NULL) set_allocation_type(res, C_HEAP);)
    break;
   case RESOURCE_AREA:
    // new(size) sets allocation type RESOURCE_AREA.
    res = (address)operator new(size, std::nothrow);
    break;
   default:
    ShouldNotReachHere();
  }
  return res;
}

void* ResourceObj::operator new [](size_t size, const std::nothrow_t&  nothrow_constant,
    allocation_type type, MEMFLAGS flags) throw() {
  return (address)operator new(size, nothrow_constant, type, flags);
}

void ResourceObj::operator delete(void* p) {
  assert(((ResourceObj *)p)->allocated_on_C_heap(),
         "delete only allowed for C_HEAP objects");
  DEBUG_ONLY(((ResourceObj *)p)->_allocation_t[0] = (uintptr_t)badHeapOopVal;)
  FreeHeap(p);
}

void ResourceObj::operator delete [](void* p) {
  operator delete(p);
}

#ifdef ASSERT
void ResourceObj::set_allocation_type(address res, allocation_type type) {
    // Set allocation type in the resource object
    uintptr_t allocation = (uintptr_t)res;
    assert((allocation & allocation_mask) == 0, "address should be aligned to 4 bytes at least: " INTPTR_FORMAT, p2i(res));
    assert(type <= allocation_mask, "incorrect allocation type");
    ResourceObj* resobj = (ResourceObj *)res;
    resobj->_allocation_t[0] = ~(allocation + type);
    if (type != STACK_OR_EMBEDDED) {
      // Called from operator new() and CollectionSetChooser(),
      // set verification value.
      resobj->_allocation_t[1] = (uintptr_t)&(resobj->_allocation_t[1]) + type;
    }
}

ResourceObj::allocation_type ResourceObj::get_allocation_type() const {
    assert(~(_allocation_t[0] | allocation_mask) == (uintptr_t)this, "lost resource object");
    return (allocation_type)((~_allocation_t[0]) & allocation_mask);
}

bool ResourceObj::is_type_set() const {
    allocation_type type = (allocation_type)(_allocation_t[1] & allocation_mask);
    return get_allocation_type()  == type &&
           (_allocation_t[1] - type) == (uintptr_t)(&_allocation_t[1]);
}

ResourceObj::ResourceObj() { // default constructor
    if (~(_allocation_t[0] | allocation_mask) != (uintptr_t)this) {
      // Operator new() is not called for allocations
      // on stack and for embedded objects.
      set_allocation_type((address)this, STACK_OR_EMBEDDED);
    } else if (allocated_on_stack()) { // STACK_OR_EMBEDDED
      // For some reason we got a value which resembles
      // an embedded or stack object (operator new() does not
      // set such type). Keep it since it is valid value
      // (even if it was garbage).
      // Ignore garbage in other fields.
    } else if (is_type_set()) {
      // Operator new() was called and type was set.
      assert(!allocated_on_stack(),
             "not embedded or stack, this(" PTR_FORMAT ") type %d a[0]=(" PTR_FORMAT ") a[1]=(" PTR_FORMAT ")",
             p2i(this), get_allocation_type(), _allocation_t[0], _allocation_t[1]);
    } else {
      // Operator new() was not called.
      // Assume that it is embedded or stack object.
      set_allocation_type((address)this, STACK_OR_EMBEDDED);
    }
    _allocation_t[1] = 0; // Zap verification value
}

ResourceObj::ResourceObj(const ResourceObj& r) { // default copy constructor
    // Used in ClassFileParser::parse_constant_pool_entries() for ClassFileStream.
    // Note: garbage may resembles valid value.
    assert(~(_allocation_t[0] | allocation_mask) != (uintptr_t)this || !is_type_set(),
           "embedded or stack only, this(" PTR_FORMAT ") type %d a[0]=(" PTR_FORMAT ") a[1]=(" PTR_FORMAT ")",
           p2i(this), get_allocation_type(), _allocation_t[0], _allocation_t[1]);
    set_allocation_type((address)this, STACK_OR_EMBEDDED);
    _allocation_t[1] = 0; // Zap verification value
}

ResourceObj& ResourceObj::operator=(const ResourceObj& r) { // default copy assignment
    // Used in InlineTree::ok_to_inline() for WarmCallInfo.
    assert(allocated_on_stack(),
           "copy only into local, this(" PTR_FORMAT ") type %d a[0]=(" PTR_FORMAT ") a[1]=(" PTR_FORMAT ")",
           p2i(this), get_allocation_type(), _allocation_t[0], _allocation_t[1]);
    // Keep current _allocation_t value;
    return *this;
}

ResourceObj::~ResourceObj() {
    // allocated_on_C_heap() also checks that encoded (in _allocation) address == this.
    if (!allocated_on_C_heap()) { // ResourceObj::delete() will zap _allocation for C_heap.
      _allocation_t[0] = (uintptr_t)badHeapOopVal; // zap type
    }
}
#endif // ASSERT

//--------------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT
void AllocatedObj::print() const       { print_on(tty); }
void AllocatedObj::print_value() const { print_value_on(tty); }

void AllocatedObj::print_on(outputStream* st) const {
  st->print_cr("AllocatedObj(" INTPTR_FORMAT ")", p2i(this));
}

void AllocatedObj::print_value_on(outputStream* st) const {
  st->print("AllocatedObj(" INTPTR_FORMAT ")", p2i(this));
}

AllocStats::AllocStats() {
  start_mallocs      = os::num_mallocs;
  start_frees        = os::num_frees;
  start_malloc_bytes = os::alloc_bytes;
  start_mfree_bytes  = os::free_bytes;
  start_res_bytes    = Arena::_bytes_allocated;
}

julong  AllocStats::num_mallocs() { return os::num_mallocs - start_mallocs; }
julong  AllocStats::alloc_bytes() { return os::alloc_bytes - start_malloc_bytes; }
julong  AllocStats::num_frees()   { return os::num_frees - start_frees; }
julong  AllocStats::free_bytes()  { return os::free_bytes - start_mfree_bytes; }
julong  AllocStats::resource_bytes() { return Arena::_bytes_allocated - start_res_bytes; }
void    AllocStats::print() {
  tty->print_cr(UINT64_FORMAT " mallocs (" UINT64_FORMAT "MB), "
                UINT64_FORMAT " frees (" UINT64_FORMAT "MB), " UINT64_FORMAT "MB resrc",
                num_mallocs(), alloc_bytes()/M, num_frees(), free_bytes()/M, resource_bytes()/M);
}

ReallocMark::ReallocMark() {
#ifdef ASSERT
  Thread *thread = Thread::current();
  _nesting = thread->resource_area()->nesting();
#endif
}

void ReallocMark::check() {
#ifdef ASSERT
  if (_nesting != Thread::current()->resource_area()->nesting()) {
    fatal("allocation bug: array could grow within nested ResourceMark");
  }
#endif
}

#endif // Non-product
