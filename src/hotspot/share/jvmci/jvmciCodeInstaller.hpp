/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JVMCI_JVMCI_CODE_INSTALLER_HPP
#define SHARE_VM_JVMCI_JVMCI_CODE_INSTALLER_HPP

#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "code/nativeInst.hpp"

class RelocBuffer : public StackObj {
  enum { stack_size = 1024 };
public:
  RelocBuffer() : _size(0), _buffer(0) {}
  ~RelocBuffer();
  void ensure_size(size_t bytes);
  void set_size(size_t bytes);
  address begin() const;
  size_t size() const { return _size; }
private:
  size_t _size;
  char _static_buffer[stack_size];
  char *_buffer;
};

class AOTOopRecorder : public OopRecorder {
public:
  AOTOopRecorder(Arena* arena = NULL, bool deduplicate = false);

  virtual int find_index(Metadata* h);
  virtual int find_index(jobject h);
  int nr_meta_refs() const;
  jobject meta_element(int pos) const;

private:
  void record_meta_ref(jobject ref, int index);

  GrowableArray<jobject>* _meta_refs;
};

class CodeMetadata {
public:
  CodeMetadata() {}

  CodeBlob* get_code_blob() const { return _cb; }

  PcDesc* get_pc_desc() const { return _pc_desc; }
  int get_nr_pc_desc() const { return _nr_pc_desc; }

  u_char* get_scopes_desc() const { return _scopes_desc; }
  int get_scopes_size() const { return _nr_scopes_desc; }

  RelocBuffer* get_reloc_buffer() { return &_reloc_buffer; }

  AOTOopRecorder* get_oop_recorder() { return _oop_recorder; }

  ExceptionHandlerTable* get_exception_table() { return _exception_table; }

  void set_pc_desc(PcDesc* desc, int count) {
    _pc_desc = desc;
    _nr_pc_desc = count;
  }

  void set_scopes(u_char* scopes, int size) {
    _scopes_desc = scopes;
    _nr_scopes_desc = size;
  }

  void set_oop_recorder(AOTOopRecorder* recorder) {
    _oop_recorder = recorder;
  }

  void set_exception_table(ExceptionHandlerTable* table) {
    _exception_table = table;
  }

private:
  CodeBlob* _cb;
  PcDesc* _pc_desc;
  int _nr_pc_desc;

  u_char* _scopes_desc;
  int _nr_scopes_desc;

  RelocBuffer _reloc_buffer;
  AOTOopRecorder* _oop_recorder;
  ExceptionHandlerTable* _exception_table;
};

/*
 * This class handles the conversion from a InstalledCode to a CodeBlob or an nmethod.
 */
class CodeInstaller : public StackObj {
  friend class JVMCIVMStructs;
private:
  enum MarkId {
    INVALID_MARK,
    VERIFIED_ENTRY,
    UNVERIFIED_ENTRY,
    OSR_ENTRY,
    EXCEPTION_HANDLER_ENTRY,
    DEOPT_HANDLER_ENTRY,
    FRAME_COMPLETE,
    INVOKEINTERFACE,
    INVOKEVIRTUAL,
    INVOKESTATIC,
    INVOKESPECIAL,
    INLINE_INVOKE,
    POLL_NEAR,
    POLL_RETURN_NEAR,
    POLL_FAR,
    POLL_RETURN_FAR,
    CARD_TABLE_ADDRESS,
    CARD_TABLE_SHIFT,
    HEAP_TOP_ADDRESS,
    HEAP_END_ADDRESS,
    NARROW_KLASS_BASE_ADDRESS,
    NARROW_OOP_BASE_ADDRESS,
    CRC_TABLE_ADDRESS,
    LOG_OF_HEAP_REGION_GRAIN_BYTES,
    INLINE_CONTIGUOUS_ALLOCATION_SUPPORTED,
    INVOKE_INVALID                         = -1
  };

  Arena         _arena;

  jobject       _data_section_handle;
  jobject       _data_section_patches_handle;
  jobject       _sites_handle;
  CodeOffsets   _offsets;

  jobject       _code_handle;
  jint          _code_size;
  jint          _total_frame_size;
  jint          _orig_pc_offset;
  jint          _parameter_count;
  jint          _constants_size;
#ifndef PRODUCT
  jobject       _comments_handle;
#endif

  bool          _has_wide_vector;
  jobject       _word_kind_handle;

  MarkId        _next_call_type;
  address       _invoke_mark_pc;

  CodeSection*  _instructions;
  CodeSection*  _constants;

  OopRecorder*              _oop_recorder;
  DebugInformationRecorder* _debug_recorder;
  Dependencies*             _dependencies;
  ExceptionHandlerTable     _exception_handler_table;

  bool _immutable_pic_compilation;  // Installer is called for Immutable PIC compilation.

  static ConstantOopWriteValue* _oop_null_scope_value;
  static ConstantIntValue*    _int_m1_scope_value;
  static ConstantIntValue*    _int_0_scope_value;
  static ConstantIntValue*    _int_1_scope_value;
  static ConstantIntValue*    _int_2_scope_value;
  static LocationValue*       _illegal_value;

  jint pd_next_offset(NativeInstruction* inst, jint pc_offset, Handle method, TRAPS);
  void pd_patch_OopConstant(int pc_offset, Handle constant, TRAPS);
  void pd_patch_MetaspaceConstant(int pc_offset, Handle constant, TRAPS);
  void pd_patch_DataSectionReference(int pc_offset, int data_offset, TRAPS);
  void pd_relocate_ForeignCall(NativeInstruction* inst, jlong foreign_call_destination, TRAPS);
  void pd_relocate_JavaMethod(CodeBuffer &cbuf, Handle method, jint pc_offset, TRAPS);
  void pd_relocate_poll(address pc, jint mark, TRAPS);

  objArrayOop sites();
  arrayOop code();
  arrayOop data_section();
  objArrayOop data_section_patches();
#ifndef PRODUCT
  objArrayOop comments();
#endif

  oop word_kind();

public:

  CodeInstaller(bool immutable_pic_compilation) : _arena(mtCompiler), _immutable_pic_compilation(immutable_pic_compilation) {}

  JVMCIEnv::CodeInstallResult gather_metadata(Handle target, Handle compiled_code, CodeMetadata& metadata, TRAPS);
  JVMCIEnv::CodeInstallResult install(JVMCICompiler* compiler, Handle target, Handle compiled_code, CodeBlob*& cb, Handle installed_code, Handle speculation_log, TRAPS);

  static address runtime_call_target_address(oop runtime_call);
  static VMReg get_hotspot_reg(jint jvmciRegisterNumber, TRAPS);
  static bool is_general_purpose_reg(VMReg hotspotRegister);

  const OopMapSet* oopMapSet() const { return _debug_recorder->_oopmaps; }

protected:
  Location::Type get_oop_type(Thread* thread, Handle value);
  ScopeValue* get_scope_value(Handle value, BasicType type, GrowableArray<ScopeValue*>* objects, ScopeValue* &second, TRAPS);
  MonitorValue* get_monitor_value(Handle value, GrowableArray<ScopeValue*>* objects, TRAPS);

  void* record_metadata_reference(CodeSection* section, address dest, Handle constant, TRAPS);
#ifdef _LP64
  narrowKlass record_narrow_metadata_reference(CodeSection* section, address dest, Handle constant, TRAPS);
#endif

  // extract the fields of the HotSpotCompiledCode
  void initialize_fields(oop target, oop target_method, TRAPS);
  void initialize_dependencies(oop target_method, OopRecorder* oop_recorder, TRAPS);

  int estimate_stubs_size(TRAPS);

  // perform data and call relocation on the CodeBuffer
  JVMCIEnv::CodeInstallResult initialize_buffer(CodeBuffer& buffer, bool check_size, TRAPS);

  void assumption_NoFinalizableSubclass(Thread* thread, Handle assumption);
  void assumption_ConcreteSubtype(Thread* thread, Handle assumption);
  void assumption_LeafType(Thread* thread, Handle assumption);
  void assumption_ConcreteMethod(Thread* thread, Handle assumption);
  void assumption_CallSiteTargetValue(Thread* thread, Handle assumption);

  void site_Safepoint(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS);
  void site_Infopoint(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS);
  void site_Call(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS);
  void site_DataPatch(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS);
  void site_Mark(CodeBuffer& buffer, jint pc_offset, Handle site, TRAPS);
  void site_ExceptionHandler(jint pc_offset, Handle site);

  OopMap* create_oop_map(Handle debug_info, TRAPS);

  /**
   * Specifies the level of detail to record for a scope.
   */
  enum ScopeMode {
    // Only record a method and BCI
    BytecodePosition,
    // Record a method, bci and JVM frame state
    FullFrame
  };

  int map_jvmci_bci(int bci);
  void record_scope(jint pc_offset, Handle debug_info, ScopeMode scope_mode, bool return_oop, TRAPS);
  void record_scope(jint pc_offset, Handle debug_info, ScopeMode scope_mode, TRAPS) {
    record_scope(pc_offset, debug_info, scope_mode, false /* return_oop */, THREAD);
  }
  void record_scope(jint pc_offset, Handle position, ScopeMode scope_mode, GrowableArray<ScopeValue*>* objects, bool return_oop, TRAPS);
  void record_object_value(ObjectValue* sv, Handle value, GrowableArray<ScopeValue*>* objects, TRAPS);

  GrowableArray<ScopeValue*>* record_virtual_objects(Handle debug_info, TRAPS);

  int estimateStubSpace(int static_call_stubs);
};

/**
 * Gets the Method metaspace object from a HotSpotResolvedJavaMethodImpl Java object.
 */
Method* getMethodFromHotSpotMethod(oop hotspot_method);



#endif // SHARE_VM_JVMCI_JVMCI_CODE_INSTALLER_HPP
