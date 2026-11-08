package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;

/// Builds a [SequenceNode], which takes no parameters.
public record SequenceNodeBlueprint() implements BehaviorNodeBlueprint<SequenceNode> {
    public static final MapCodec<SequenceNodeBlueprint> CODEC = MapCodec.unit(SequenceNodeBlueprint::new);

    @Override
    public SequenceNode instantiate(final BrainComponent blueprintOwner) {
        return new SequenceNode();
    }

    @Override
    public Class<SequenceNode> nodeClass() {
        return SequenceNode.class;
    }

    @Override
    public MapCodec<SequenceNodeBlueprint> mapCodec() {
        return CODEC;
    }
}
