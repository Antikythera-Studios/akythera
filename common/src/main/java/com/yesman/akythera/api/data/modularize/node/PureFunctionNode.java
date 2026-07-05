package com.yesman.akythera.api.data.modularize.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.FunctionSignature;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/// This node consists the tree hierarchy to structure [FlowFunctionNode#params()]
/// or another pure node's [PureFunctionNode#params] – A list of provider functions that
/// [#function()] requires to execute.
public record PureFunctionNode(ScriptFunction function, List<PureNode> params) implements FunctionNode, PureNode {
    /// Recursive node codec for the execution tree: `{ "function": <id>, "params": [ <node>... ] }`.
    public static final Codec<PureFunctionNode> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec()
                        .fieldOf("function")
                        .forGetter(PureFunctionNode::function),
                    PureNode.CODEC.listOf()
                        .optionalFieldOf("params", List.of())
                        .forGetter(PureFunctionNode::params)
                )
                .apply(instance, PureFunctionNode::new)
        );

    public static Builder builder(final ScriptFunction function) {
        return new Builder(function);
    }

    public static final class Builder {
        final ScriptFunction function;
        final List<PureNode> parameterFunctions = new ArrayList<>();

        private Builder(final ScriptFunction function) {
            if (!function.isPure()) {
                throw new IllegalArgumentException("Only pure functions can be on the PureFunctionNode!");
            }

            this.function = function;
        }

        /// Appends `parameter` as the next argument of this node. The parameter's function return type
        /// must be assignable to the corresponding slot of this function's signature.
        ///
        /// @throws IllegalStateException    when this node's parameters are already full.
        /// @throws IllegalArgumentException when the parameter's return type is incompatible with the slot.
        public PureFunctionNode.Builder addParameterFunction(final PureNode paramProviderNode) {
            FunctionSignature signature = function.functionSignature();
            int slot = parameterFunctions.size();

            if (slot >= signature.parameters().size()) {
                throw new IllegalStateException(
                    function.metadata().label() + " accepts only " + signature.parameters().size() + " parameter(s); cannot add more."
                );
            }

            Class<?> providerType = null;

            // Type check only comparable nodes
            if (paramProviderNode instanceof LiteralNode<?> literalNode) {
                providerType = literalNode.type().type();
            } else if (paramProviderNode instanceof PureFunctionNode pureFunctionNode) {
                providerType = pureFunctionNode.function().functionSignature().returnType();
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

        /// Recursively builds the execution tree, requiring every declared parameter to be filled.
        ///
        /// @throws IllegalStateException when one or more required parameters are missing.
        public PureFunctionNode build() {
            FunctionSignature signature = function.functionSignature();

            if (parameterFunctions.size() != signature.parameters().size()) {
                throw new IllegalStateException(
                    function.metadata().label() + " requires " + signature.parameters().size()
                        + " parameter(s) but " + parameterFunctions.size() + " were provided."
                );
            }

            List<PureNode> params = new ArrayList<>(parameterFunctions.size());
            params.addAll(parameterFunctions);

            return new PureFunctionNode(function, params);
        }
    }

    @Override
    public @NonNull String toString() {
        Identifier nodeIdentifier = AkytheraRegistries.Frozen.SCRIPT_FUNCTION.getKey(function());
        return nodeIdentifier == null ? "Dangling function" : nodeIdentifier.toString();
    }
}
