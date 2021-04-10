/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/occupancyMap.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

size_t Metachunk::object_alignment() {
  // Must align pointers and sizes to 8,
  // so that 64 bit types get correctly aligned.
  const size_t alignment = 8;

  // Make sure that the Klass alignment also agree.
  STATIC_ASSERT(alignment == (size_t)KlassAlignmentInBytes);

  return alignment;
}

size_t Metachunk::overhead() {
  return align_up(sizeof(Metachunk), object_alignment()) / BytesPerWord;
}

// Metachunk methods

Metachunk::Metachunk(ChunkIndex chunktype, bool is_class, size_t word_size,
                     VirtualSpaceNode* container)
    : Metabase<Metachunk>(word_size),
    _chunk_type(chunktype),
    _is_class(is_class),
    _sentinel(CHUNK_SENTINEL),
    _origin(origin_normal),
    _use_count(0),
    _top(NULL),
    _container(container)
{
  _top = initial_top();
  set_is_tagged_free(false);
#ifdef ASSERT
  mangle(uninitMetaWordVal);
  verify();
#endif
}

MetaWord* Metachunk::allocate(size_t word_size) {
  MetaWord* result = NULL;
  // If available, bump the pointer to allocate.
  if (free_word_size() >= word_size) {
    result = _top;
    _top = _top + word_size;
  }
  return result;
}

// _bottom points to the start of the chunk including the overhead.
size_t Metachunk::used_word_size() const {
  return pointer_delta(_top, bottom(), sizeof(MetaWord));
}

size_t Metachunk::free_word_size() const {
  return pointer_delta(end(), _top, sizeof(MetaWord));
}

void Metachunk::print_on(outputStream* st) const {
  st->print_cr("Metachunk:"
               " bottom " PTR_FORMAT " top " PTR_FORMAT
               " end " PTR_FORMAT " size " SIZE_FORMAT " (%s)",
               p2i(bottom()), p2i(_top), p2i(end()), word_size(),
               chunk_size_name(get_chunk_type()));
  if (Verbose) {
    st->print_cr("    used " SIZE_FORMAT " free " SIZE_FORMAT,
                 used_word_size(), free_word_size());
  }
}

#ifdef ASSERT
void Metachunk::mangle(juint word_value) {
  // Overwrite the payload of the chunk and not the links that
  // maintain list of chunks.
  HeapWord* start = (HeapWord*)initial_top();
  size_t size = word_size() - overhead();
  Copy::fill_to_words(start, size, word_value);
}

void Metachunk::verify() const {
  assert(is_valid_sentinel(), "Chunk " PTR_FORMAT ": sentinel invalid", p2i(this));
  const ChunkIndex chunk_type = get_chunk_type();
  assert(is_valid_chunktype(chunk_type), "Chunk " PTR_FORMAT ": Invalid chunk type.", p2i(this));
  if (chunk_type != HumongousIndex) {
    assert(word_size() == get_size_for_nonhumongous_chunktype(chunk_type, is_class()),
           "Chunk " PTR_FORMAT ": wordsize " SIZE_FORMAT " does not fit chunk type %s.",
           p2i(this), word_size(), chunk_size_name(chunk_type));
  }
  assert(is_valid_chunkorigin(get_origin()), "Chunk " PTR_FORMAT ": Invalid chunk origin.", p2i(this));
  assert(bottom() <= _top && _top <= (MetaWord*)end(),
         "Chunk " PTR_FORMAT ": Chunk top out of chunk bounds.", p2i(this));

  // For non-humongous chunks, starting address shall be aligned
  // to its chunk size. Humongous chunks start address is
  // aligned to specialized chunk size.
  const size_t required_alignment =
    (chunk_type != HumongousIndex ? word_size() : get_size_for_nonhumongous_chunktype(SpecializedIndex, is_class())) * sizeof(MetaWord);
  assert(is_aligned((address)this, required_alignment),
         "Chunk " PTR_FORMAT ": (size " SIZE_FORMAT ") not aligned to " SIZE_FORMAT ".",
         p2i(this), word_size() * sizeof(MetaWord), required_alignment);
}

#endif // ASSERT

// Helper, returns a descriptive name for the given index.
const char* chunk_size_name(ChunkIndex index) {
  switch (index) {
    case SpecializedIndex:
      return "specialized";
    case SmallIndex:
      return "small";
    case MediumIndex:
      return "medium";
    case HumongousIndex:
      return "humongous";
    default:
      return "Invalid index";
  }
}

#ifdef ASSERT
void do_verify_chunk(Metachunk* chunk) {
  guarantee(chunk != NULL, "Sanity");
  // Verify chunk itself; then verify that it is consistent with the
  // occupany map of its containing node.
  chunk->verify();
  VirtualSpaceNode* const vsn = chunk->container();
  OccupancyMap* const ocmap = vsn->occupancy_map();
  ocmap->verify_for_chunk(chunk);
}
#endif

void do_update_in_use_info_for_chunk(Metachunk* chunk, bool inuse) {
  chunk->set_is_tagged_free(!inuse);
  OccupancyMap* const ocmap = chunk->container()->occupancy_map();
  ocmap->set_region_in_use((MetaWord*)chunk, chunk->word_size(), inuse);
}

} // namespace metaspace

