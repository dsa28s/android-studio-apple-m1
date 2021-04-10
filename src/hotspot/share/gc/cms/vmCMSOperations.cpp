/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/cms/cmsHeap.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.inline.hpp"
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/cms/vmCMSOperations.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/dtrace.hpp"

//////////////////////////////////////////////////////////
// Methods in abstract class VM_CMS_Operation
//////////////////////////////////////////////////////////
void VM_CMS_Operation::verify_before_gc() {
  if (VerifyBeforeGC &&
      CMSHeap::heap()->total_collections() >= VerifyGCStartAt) {
    GCTraceTime(Info, gc, phases, verify) tm("Verify Before", _collector->_gc_timer_cm);
    HandleMark hm;
    FreelistLocker x(_collector);
    MutexLockerEx  y(_collector->bitMapLock(), Mutex::_no_safepoint_check_flag);
    CMSHeap::heap()->prepare_for_verify();
    Universe::verify();
  }
}

void VM_CMS_Operation::verify_after_gc() {
  if (VerifyAfterGC &&
      CMSHeap::heap()->total_collections() >= VerifyGCStartAt) {
    GCTraceTime(Info, gc, phases, verify) tm("Verify After", _collector->_gc_timer_cm);
    HandleMark hm;
    FreelistLocker x(_collector);
    MutexLockerEx  y(_collector->bitMapLock(), Mutex::_no_safepoint_check_flag);
    Universe::verify();
  }
}

bool VM_CMS_Operation::lost_race() const {
  if (CMSCollector::abstract_state() == CMSCollector::Idling) {
    // We lost a race to a foreground collection
    // -- there's nothing to do
    return true;
  }
  assert(CMSCollector::abstract_state() == legal_state(),
         "Inconsistent collector state?");
  return false;
}

bool VM_CMS_Operation::doit_prologue() {
  assert(Thread::current()->is_ConcurrentGC_thread(), "just checking");
  assert(!CMSCollector::foregroundGCShouldWait(), "Possible deadlock");
  assert(!ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "Possible deadlock");

  Heap_lock->lock();
  if (lost_race()) {
    assert(_prologue_succeeded == false, "Initialized in c'tor");
    Heap_lock->unlock();
  } else {
    _prologue_succeeded = true;
  }
  return _prologue_succeeded;
}

void VM_CMS_Operation::doit_epilogue() {
  assert(Thread::current()->is_ConcurrentGC_thread(), "just checking");
  assert(!CMSCollector::foregroundGCShouldWait(), "Possible deadlock");
  assert(!ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "Possible deadlock");

  if (Universe::has_reference_pending_list()) {
    Heap_lock->notify_all();
  }
  Heap_lock->unlock();
}

//////////////////////////////////////////////////////////
// Methods in class VM_CMS_Initial_Mark
//////////////////////////////////////////////////////////
void VM_CMS_Initial_Mark::doit() {
  if (lost_race()) {
    // Nothing to do.
    return;
  }
  HS_PRIVATE_CMS_INITMARK_BEGIN();
  GCIdMark gc_id_mark(_gc_id);

  _collector->_gc_timer_cm->register_gc_pause_start("Initial Mark");

  CMSHeap* heap = CMSHeap::heap();
  GCCauseSetter gccs(heap, GCCause::_cms_initial_mark);

  VM_CMS_Operation::verify_before_gc();

  IsGCActiveMark x; // stop-world GC active
  _collector->do_CMS_operation(CMSCollector::CMS_op_checkpointRootsInitial, heap->gc_cause());

  VM_CMS_Operation::verify_after_gc();

  _collector->_gc_timer_cm->register_gc_pause_end();

  HS_PRIVATE_CMS_INITMARK_END();
}

//////////////////////////////////////////////////////////
// Methods in class VM_CMS_Final_Remark_Operation
//////////////////////////////////////////////////////////
void VM_CMS_Final_Remark::doit() {
  if (lost_race()) {
    // Nothing to do.
    return;
  }
  HS_PRIVATE_CMS_REMARK_BEGIN();
  GCIdMark gc_id_mark(_gc_id);

  _collector->_gc_timer_cm->register_gc_pause_start("Final Mark");

  CMSHeap* heap = CMSHeap::heap();
  GCCauseSetter gccs(heap, GCCause::_cms_final_remark);

  VM_CMS_Operation::verify_before_gc();

  IsGCActiveMark x; // stop-world GC active
  _collector->do_CMS_operation(CMSCollector::CMS_op_checkpointRootsFinal, heap->gc_cause());

  VM_CMS_Operation::verify_after_gc();

  _collector->save_heap_summary();
  _collector->_gc_timer_cm->register_gc_pause_end();

  HS_PRIVATE_CMS_REMARK_END();
}

// VM operation to invoke a concurrent collection of a
// GenCollectedHeap heap.
void VM_GenCollectFullConcurrent::doit() {
  assert(Thread::current()->is_VM_thread(), "Should be VM thread");
  assert(GCLockerInvokesConcurrent || ExplicitGCInvokesConcurrent, "Unexpected");

  CMSHeap* heap = CMSHeap::heap();
  if (_gc_count_before == heap->total_collections()) {
    // The "full" of do_full_collection call below "forces"
    // a collection; the second arg, 0, below ensures that
    // only the young gen is collected. XXX In the future,
    // we'll probably need to have something in this interface
    // to say do this only if we are sure we will not bail
    // out to a full collection in this attempt, but that's
    // for the future.
    assert(SafepointSynchronize::is_at_safepoint(),
      "We can only be executing this arm of if at a safepoint");
    GCCauseSetter gccs(heap, _gc_cause);
    heap->do_full_collection(heap->must_clear_all_soft_refs(), GenCollectedHeap::YoungGen);
  } // Else no need for a foreground young gc
  assert((_gc_count_before < heap->total_collections()) ||
         (GCLocker::is_active() /* gc may have been skipped */
          && (_gc_count_before == heap->total_collections())),
         "total_collections() should be monotonically increasing");

  MutexLockerEx x(FullGCCount_lock, Mutex::_no_safepoint_check_flag);
  assert(_full_gc_count_before <= heap->total_full_collections(), "Error");
  if (heap->total_full_collections() == _full_gc_count_before) {
    // Nudge the CMS thread to start a concurrent collection.
    CMSCollector::request_full_gc(_full_gc_count_before, _gc_cause);
  } else {
    assert(_full_gc_count_before < heap->total_full_collections(), "Error");
    FullGCCount_lock->notify_all();  // Inform the Java thread its work is done
  }
}

bool VM_GenCollectFullConcurrent::evaluate_at_safepoint() const {
  Thread* thr = Thread::current();
  assert(thr != NULL, "Unexpected tid");
  if (!thr->is_Java_thread()) {
    assert(thr->is_VM_thread(), "Expected to be evaluated by VM thread");
    CMSHeap* heap = CMSHeap::heap();
    if (_gc_count_before != heap->total_collections()) {
      // No need to do a young gc, we'll just nudge the CMS thread
      // in the doit() method above, to be executed soon.
      assert(_gc_count_before < heap->total_collections(),
             "total_collections() should be monotonically increasing");
      return false;  // no need for foreground young gc
    }
  }
  return true;       // may still need foreground young gc
}


void VM_GenCollectFullConcurrent::doit_epilogue() {
  Thread* thr = Thread::current();
  assert(thr->is_Java_thread(), "just checking");
  JavaThread* jt = (JavaThread*)thr;

  if (Universe::has_reference_pending_list()) {
    Heap_lock->notify_all();
  }
  Heap_lock->unlock();

  // It is fine to test whether completed collections has
  // exceeded our request count without locking because
  // the completion count is monotonically increasing;
  // this will break for very long-running apps when the
  // count overflows and wraps around. XXX fix me !!!
  // e.g. at the rate of 1 full gc per ms, this could
  // overflow in about 1000 years.
  CMSHeap* heap = CMSHeap::heap();
  if (_gc_cause != GCCause::_gc_locker &&
      heap->total_full_collections_completed() <= _full_gc_count_before) {
    // maybe we should change the condition to test _gc_cause ==
    // GCCause::_java_lang_system_gc or GCCause::_dcmd_gc_run,
    // instead of _gc_cause != GCCause::_gc_locker
    assert(GCCause::is_user_requested_gc(_gc_cause),
           "the only way to get here if this was a System.gc()-induced GC");
    assert(ExplicitGCInvokesConcurrent, "Error");
    // Now, wait for witnessing concurrent gc cycle to complete,
    // but do so in native mode, because we want to lock the
    // FullGCEvent_lock, which may be needed by the VM thread
    // or by the CMS thread, so we do not want to be suspended
    // while holding that lock.
    ThreadToNativeFromVM native(jt);
    MutexLockerEx ml(FullGCCount_lock, Mutex::_no_safepoint_check_flag);
    // Either a concurrent or a stop-world full gc is sufficient
    // witness to our request.
    while (heap->total_full_collections_completed() <= _full_gc_count_before) {
      FullGCCount_lock->wait(Mutex::_no_safepoint_check_flag);
    }
  }
}
