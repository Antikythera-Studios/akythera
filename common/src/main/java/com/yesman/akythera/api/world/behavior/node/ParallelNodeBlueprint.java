package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;

/// The parameters of a [ParallelNode].
public record ParallelNodeBlueprint(ParallelNode.FinishMode finishMode) implements BehaviorNodeBlueprint<ParallelNode> {
    public static final MapCodec<ParallelNodeBlueprint> CODEC = ParallelNode.FinishMode.CODEC.optionalFieldOf("finish_mode", ParallelNode.FinishMode.IMMEDIATE)
        .xmap(ParallelNodeBlueprint::new, ParallelNodeBlueprint::finishMode);

    @Override
    public ParallelNode instantiate(final BrainComponent blueprintOwner) {
        return new ParallelNode(finishMode);
    }

    @Override
    public Class<ParallelNode> nodeClass() {
        return ParallelNode.class;
    }

    @Override
    public MapCodec<ParallelNodeBlueprint> mapCodec() {
        return CODEC;
    }
}
