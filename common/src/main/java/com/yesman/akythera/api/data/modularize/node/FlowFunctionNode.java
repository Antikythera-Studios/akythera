package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// A base node that executes pure and non-pure functions.
///
/// This node becomes an entry point of node sequences. Only common
/// functions can be here. See [ScriptFunction.Type].
///
/// @param nextNode null if this node is the last flow.
public record FlowFunctionNode(
    ScriptFunction function, List<PureNode> params, @Nullable FlowNode nextNode, int nodeId
) implements FlowNode, FunctionNode
{
    /// A node with no canvas row behind it — the builders, and any hand-assembled tree.
    public FlowFunctionNode(
        final ScriptFunction function, final List<PureNode> params, final @Nullable FlowNode nextNode
    ) {
        this(function, params, nextNode, NO_NODE_ID);
    }

    /// Recursive node codec for the execution tree: `{ "function": <id>, "params": [ <node>... ] }`.
    public static final Codec<FlowFunctionNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec()
                        .fieldOf("function")
                        .forGetter(FlowFunctionNode::function),
                    PureNode.CODEC.listOf()
                        .optionalFieldOf("params", List.of())
                        .forGetter(FlowFunctionNode::params),
                    FlowNode.CODEC
                        .optionalFieldOf("next")
                        .forGetter(ParseUtils.provideOptional(FlowFunctionNode::nextNode)),
                    Codec.INT
                        .optionalFieldOf("node_id", NO_NODE_ID)
                        .forGetter(FlowFunctionNode::nodeId)
                )
                .apply(instance, (arg1, arg2, arg3, arg4) -> new FlowFunctionNode(arg1, arg2, arg3.orElse(null), arg4))
        );

    /// Runs the call, then the statement chained after it.
    ///
    /// [FunctionNode] runs the call itself and closes this node's frame before [#nextNode] starts, so
    /// the chain records as a run of siblings.
    @Override
    public <T> T execute(final DataDrivenScript<?, ?> script, final ExecutionContext context) {
        T result = FunctionNode.super.execute(script, context);

        if (nextNode != null && !context.isReturning()) {
            nextNode.execute(script, context);
        }

        return result;
    }

    public static FlowFunctionNode.Builder builder(final ScriptFunction function) {
        return new FlowFunctionNode.Builder(function);
    }

    public static final class Builder {
        final ScriptFunction function;
        final List<PureNode> parameterFunctions = new ArrayList<>();
        @Nullable FlowNode nextNode;

        private Builder(final ScriptFunction function) {
            if (function.isPure()) {
                throw new IllegalArgumentException("Pure node can't be on a TaskNode!");
            }

            this.function = function;
        }

        /// Appends a parameter return function of this node. The parameter's function return type
        /// must be assignable to the corresponding slot of [#function]'s signature.
        ///
        /// @throws IllegalStateException    when this node's parameters are already full.
        /// @throws IllegalArgumentException when the parameter's return type is incompatible with the slot.
        public FlowFunctionNode.Builder addParameterFunction(final PureNode paramProviderNode) {
            FunctionSignature signature = function.functionSignature();
            int slot = parameterFunctions.size();

            if (slot >= signature.parameters().size()) {
                throw new IllegalStateException(
                    function.metadata().label() + " accepts only " + signature.parameters().size() + " parameter(s); cannot add more."
                );
            }

            Class<?> providerType = null;

            switch (paramProviderNode) {
                case LiteralNode<?> literalNode ->
                    providerType = literalNode.type().type();
                case PureFunctionNode pureFunctionNode ->
                    providerType = pureFunctionNode.function().functionSignature().returnType();
                case NumericOperatorNode _ ->
                    providerType = Number.class;
                case InvertBooleanNode _, LogicalOperationNode _ ->
                    providerType = Boolean.class;
                case SelfNode _,
                     SupplierAdapterNode _,
                     CachedPoseReferenceNode _,
                     ScriptVariableGetterNode _,
                     AnimationStateMachineNode _,
                     ContextVariableAccessNode _ -> {}
                default -> {}
            }

            if (providerType != null && !signature.isParameterCompatible(providerType, slot)) {
                throw new IllegalArgumentException(
                    "Parameter " + slot + " of " + function.metadata().label() + " expects "
                        + signature.parameters().get(slot).getSimpleName() + " but got " + providerType.getSimpleName() + "."
                );
            }

            parameterFunctions.add(paramProviderNode);

            return this;
        }

        public FlowFunctionNode.Builder nextNode(final FlowNode nextNode) {
            this.nextNode = nextNode;
            return this;
        }

        /// Recursively builds the execution tree, requiring every declared parameter to be filled.
        ///
        /// @throws IllegalStateException when one or more required parameters are missing.
        public FlowFunctionNode build() {
            FunctionSignature signature = function.functionSignature();

            if (parameterFunctions.size() != signature.parameters().size()) {
                throw new IllegalStateException(
                    function.metadata().label() + " requires " + signature.parameters().size()
                        + " parameter(s) but " + parameterFunctions.size() + " were provided."
                );
            }

            List<PureNode> params = new ArrayList<>(parameterFunctions.size());
            params.addAll(parameterFunctions);

            return new FlowFunctionNode(function, params, nextNode);
        }
    }

    @Override
    public @NonNull String toString() {
        Identifier nodeIdentifier = AkytheraRegistries.Frozen.SCRIPT_FUNCTION.getKey(function());
        return nodeIdentifier == null ? "Dangling function" : nodeIdentifier.toString();
    }
}
