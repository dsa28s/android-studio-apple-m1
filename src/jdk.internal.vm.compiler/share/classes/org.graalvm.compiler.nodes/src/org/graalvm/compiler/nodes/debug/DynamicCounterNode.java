/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.debug;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * This node can be used to add a counter to the code that will estimate the dynamic number of calls
 * by adding an increment to the compiled code. This should of course only be used for
 * debugging/testing purposes.
 *
 * A unique counter will be created for each unique name passed to the constructor. Depending on the
 * value of withContext, the name of the root method is added to the counter's name.
 */
//@formatter:off
@NodeInfo(size = SIZE_IGNORED,
          sizeRationale = "Node is a debugging node that should not be used in production.",
          cycles = CYCLES_IGNORED,
          cyclesRationale = "Node is a debugging node that should not be used in production.")
//@formatter:on
public class DynamicCounterNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<DynamicCounterNode> TYPE = NodeClass.create(DynamicCounterNode.class);
    @Input ValueNode increment;

    protected final String name;
    protected final String group;
    protected final boolean withContext;

    public DynamicCounterNode(String name, String group, ValueNode increment, boolean withContext) {
        this(TYPE, name, group, increment, withContext);
    }

    protected DynamicCounterNode(NodeClass<? extends DynamicCounterNode> c, String name, String group, ValueNode increment, boolean withContext) {
        super(c, StampFactory.forVoid());
        this.name = name;
        this.group = group;
        this.increment = increment;
        this.withContext = withContext;
    }

    public ValueNode getIncrement() {
        return increment;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public boolean isWithContext() {
        return withContext;
    }

    public static void addCounterBefore(String group, String name, long increment, boolean withContext, FixedNode position) {
        StructuredGraph graph = position.graph();
        graph.addBeforeFixed(position, position.graph().add(new DynamicCounterNode(name, group, ConstantNode.forLong(increment, position.graph()), withContext)));
    }

    @NodeIntrinsic
    public static native void counter(@ConstantNodeParameter String name, @ConstantNodeParameter String group, long increment, @ConstantNodeParameter boolean addContext);

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool lirGen = generator.getLIRGeneratorTool();
        String nameWithContext;
        if (isWithContext()) {
            nameWithContext = getName() + " @ ";
            if (graph().method() != null) {
                StackTraceElement stackTraceElement = graph().method().asStackTraceElement(0);
                if (stackTraceElement != null) {
                    nameWithContext += " " + stackTraceElement.toString();
                } else {
                    nameWithContext += graph().method().format("%h.%n");
                }
            }
            if (graph().name != null) {
                nameWithContext += " (" + graph().name + ")";
            }

        } else {
            nameWithContext = getName();
        }
        LIRInstruction counterOp = lirGen.createBenchmarkCounter(nameWithContext, getGroup(), generator.operand(increment));
        if (counterOp != null) {
            lirGen.append(counterOp);
        } else {
            throw GraalError.unimplemented("Benchmark counters not enabled or not implemented by the back end.");
        }
    }

}
