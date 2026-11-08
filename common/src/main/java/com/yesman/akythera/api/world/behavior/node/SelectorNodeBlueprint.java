package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;

/// Builds a [SelectorNode], which takes no parameters.
public record SelectorNodeBlueprint() implements BehaviorNodeBlueprint<SelectorNode> {
    public static final MapCodec<SelectorNodeBlueprint> CODEC = MapCodec.unit(SelectorNodeBlueprint::new);

    @Override
    public SelectorNode instantiate(final BrainComponent blueprintOwner) {
        return new SelectorNode();
    }

    @Override
    public Class<SelectorNode> nodeClass() {
        return SelectorNode.class;
    }

    @Override
    public MapCodec<SelectorNodeBlueprint> mapCodec() {
        return CODEC;
    }
}
