package com.yesman.akythera.api.world.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;

import java.util.function.Function;
import java.util.stream.Stream;

/// The parameters of one node of a behavior tree, which builds the node for each brain running the tree. A node
/// that keeps no state may be its own blueprint and return itself.
///
/// Dispatched on `type` through [AkytheraRegistries.Frozen#BEHAVIOR_NODE_TYPES].
///
/// @param <N> the node it builds
public interface BehaviorNodeBlueprint<N extends BehaviorNode> extends Blueprint<N, BrainComponent> {
    Codec<BehaviorNodeBlueprint<?>> CODEC = Codec.lazyInitialized(() ->
        AkytheraRegistries.Frozen.BEHAVIOR_NODE_TYPES.byNameCodec().dispatch(BehaviorNodeBlueprint::mapCodec, Function.identity())
    );

    /// [#CODEC], refusing a blueprint whose node is no `kind`.
    @SuppressWarnings("unchecked")
    static <K extends BehaviorNode> Codec<BehaviorNodeBlueprint<? extends K>> kindCodec(final Class<K> kind) {
        return CODEC.comapFlatMap(
            blueprint -> kind.isAssignableFrom(blueprint.nodeClass())
                ? DataResult.success((BehaviorNodeBlueprint<? extends K>)blueprint)
                : DataResult.error(() -> blueprint.nodeClass().getSimpleName() + " is no " + kind.getSimpleName()),
            Function.identity()
        );
    }

    /// The class of the node [#instantiate] builds.
    Class<? extends N> nodeClass();

    /// The blackboard keys the parameters name, tested against the asset's variables when the asset loads.
    default Stream<BlackboardKey> blackboardKeys() {
        return Stream.empty();
    }

    @Override
    MapCodec<? extends BehaviorNodeBlueprint<N>> mapCodec();
}
