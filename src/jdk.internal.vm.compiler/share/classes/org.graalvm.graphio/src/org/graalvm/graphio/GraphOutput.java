/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.graphio;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.Map;

/**
 * Instance of output to dump informations about a compiler compilations.
 *
 * @param <G> the type of graph this instance handles
 * @param <M> the type of methods this instance handles
 */
public final class GraphOutput<G, M> implements Closeable {
    private final GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?, ?> printer;

    private GraphOutput(GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?, ?> p) {
        this.printer = p;
    }

    /**
     * Creates new builder to configure a future instance of {@link GraphOutput}.
     *
     * @param <G> the type of the graph
     * @param <N> the type of the nodes
     * @param <C> the type of the node classes
     * @param <P> the type of the ports
     *
     * @param structure description of the structure of the graph
     * @return the builder to configure
     */
    public static <G, N, C, P> Builder<G, N, ?> newBuilder(GraphStructure<G, N, C, P> structure) {
        return new Builder<>(structure);
    }

    /**
     * Begins a compilation group.
     *
     * @param forGraph
     * @param name
     * @param shortName
     * @param method
     * @param bci
     * @param properties
     * @throws IOException
     */
    public void beginGroup(G forGraph, String name, String shortName, M method, int bci, Map<? extends Object, ? extends Object> properties) throws IOException {
        printer.beginGroup(forGraph, name, shortName, method, bci, properties);
    }

    /**
     * Prints a single graph.
     *
     * @param graph
     * @param properties
     * @param id
     * @param format
     * @param args
     * @throws IOException
     */
    public void print(G graph, Map<? extends Object, ? extends Object> properties, int id, String format, Object... args) throws IOException {
        printer.print(graph, properties, id, format, args);
    }

    /**
     * Ends compilation group.
     *
     * @throws IOException
     */
    public void endGroup() throws IOException {
        printer.endGroup();
    }

    /**
     * Closes the output. Closes allocated resources and associated output channel.
     */
    @Override
    public void close() {
        printer.close();
    }

    /**
     * Builder to configure and create an instance of {@link GraphOutput}.
     *
     * @param <G> the type of the (root element of) graph
     * @param <N> the type of the nodes
     * @param <M> the type of the methods
     */
    public static final class Builder<G, N, M> {
        private final GraphStructure<G, N, ?, ?> structure;
        private ElementsAndLocations<M, ?, ?> elementsAndLocations;

        private GraphTypes types = DefaultGraphTypes.DEFAULT;
        private GraphBlocks<G, ?, N> blocks = DefaultGraphBlocks.empty();
        private int major = 4;
        private int minor = 0;

        Builder(GraphStructure<G, N, ?, ?> structure) {
            this.structure = structure;
        }

        /**
         * Chooses which version of the protocol to use. The default version is <code>4.0</code>
         * (when the {@link GraphOutput} & co. classes were introduced). The default can be changed
         * to other known versions manually by calling this method.
         *
         * @param majorVersion by default 4, newer version may be known
         * @param minorVersion usually 0
         * @return this builder
         * @since 0.28
         */
        public Builder<G, N, M> protocolVersion(int majorVersion, int minorVersion) {
            this.major = majorVersion;
            this.minor = minorVersion;
            return this;
        }

        /**
         * Associates different implementation of types.
         *
         * @param graphTypes implementation of types and enum recognition
         * @return this builder
         */
        public Builder<G, N, M> types(GraphTypes graphTypes) {
            this.types = graphTypes;
            return this;
        }

        /**
         * Associates implementation of blocks.
         *
         * @param graphBlocks the blocks implementation
         * @return this builder
         */
        public Builder<G, N, M> blocks(GraphBlocks<G, ?, N> graphBlocks) {
            this.blocks = graphBlocks;
            return this;
        }

        /**
         * Associates implementation of graph elements.
         *
         * @param graphElements the elements implementation
         * @return this builder
         */
        public <E, P> Builder<G, N, E> elements(GraphElements<E, ?, ?, P> graphElements) {
            StackLocations<E, P> loc = new StackLocations<>(graphElements);
            return elementsAndLocations(graphElements, loc);
        }

        /**
         * Associates implementation of graph elements and an advanced way to interpret their
         * locations.
         *
         * @param graphElements the elements implementation
         * @param graphLocations the locations for the elements
         * @return this builder
         * @since 0.33 GraalVM 0.33
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <E, P> Builder<G, N, E> elementsAndLocations(GraphElements<E, ?, ?, P> graphElements, GraphLocations<E, P, ?> graphLocations) {
            ElementsAndLocations both = new ElementsAndLocations<>(graphElements, graphLocations);
            this.elementsAndLocations = both;
            return (Builder<G, N, E>) this;
        }

        /**
         * Creates new {@link GraphOutput} to output to provided channel. The output will use
         * interfaces currently associated with this builder.
         *
         * @param channel the channel to output to
         * @return new graph output
         * @throws IOException if something goes wrong when writing to the channel
         */
        public GraphOutput<G, M> build(WritableByteChannel channel) throws IOException {
            return buildImpl(elementsAndLocations, channel);
        }

        /**
         * Support for nesting heterogenous graphs. The newly created output uses all the interfaces
         * currently associated with this builder, but shares with {@code parent} the output
         * {@code channel}, internal constant pool and {@link #protocolVersion(int, int) protocol
         * version}.
         * <p>
         * Both GraphOutput (the {@code parent} and the returned one) has to be used in
         * synchronization - e.g. only one
         * {@link #beginGroup(java.lang.Object, java.lang.String, java.lang.String, java.lang.Object, int, java.util.Map)
         * begin}, {@link #endGroup() end} of group or
         * {@link #print(java.lang.Object, java.util.Map, int, java.lang.String, java.lang.Object...)
         * printing} can be on at a given moment.
         *
         * @param parent the output to inherit {@code channel} and protocol version from
         * @return new output sharing {@code channel} and other internals with {@code parent}
         */
        public GraphOutput<G, M> build(GraphOutput<?, ?> parent) {
            return buildImpl(elementsAndLocations, parent);
        }

        private <L, P> GraphOutput<G, M> buildImpl(ElementsAndLocations<M, L, P> e, WritableByteChannel channel) throws IOException {
            // @formatter:off
            ProtocolImpl<G, N, ?, ?, ?, M, ?, ?, ?, ?> p = new ProtocolImpl<>(
                major, minor, structure, types, blocks,
                e == null ? null : e.elements,
                e == null ? null : e.locations, channel
            );
            // @formatter:on
            return new GraphOutput<>(p);
        }

        private <L, P> GraphOutput<G, M> buildImpl(ElementsAndLocations<M, L, P> e, GraphOutput<?, ?> parent) {
            // @formatter:off
            ProtocolImpl<G, N, ?, ?, ?, M, ?, ?, ?, ?> p = new ProtocolImpl<>(
                parent.printer, structure, types, blocks,
                e == null ? null : e.elements,
                e == null ? null : e.locations
            );
            // @formatter:on
            return new GraphOutput<>(p);
        }
    }

    private static final class ElementsAndLocations<M, P, L> {
        final GraphElements<M, ?, ?, P> elements;
        final GraphLocations<M, P, L> locations;

        ElementsAndLocations(GraphElements<M, ?, ?, P> elements, GraphLocations<M, P, L> locations) {
            elements.getClass();
            locations.getClass();
            this.elements = elements;
            this.locations = locations;
        }
    }

    private static final class StackLocations<M, P> implements GraphLocations<M, P, StackTraceElement> {
        private final GraphElements<M, ?, ?, P> graphElements;

        StackLocations(GraphElements<M, ?, ?, P> graphElements) {
            this.graphElements = graphElements;
        }

        @Override
        public Iterable<StackTraceElement> methodLocation(M method, int bci, P pos) {
            StackTraceElement ste = this.graphElements.methodStackTraceElement(method, bci, pos);
            return Collections.singleton(ste);
        }

        @Override
        public URI locationURI(StackTraceElement location) {
            String path = location.getFileName();
            try {
                return path == null ? null : new URI(null, null, path, null);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public int locationLineNumber(StackTraceElement location) {
            return location.getLineNumber();
        }

        @Override
        public String locationLanguage(StackTraceElement location) {
            return "Java";
        }

        @Override
        public int locationOffsetStart(StackTraceElement location) {
            return -1;
        }

        @Override
        public int locationOffsetEnd(StackTraceElement location) {
            return -1;
        }
    }
}
