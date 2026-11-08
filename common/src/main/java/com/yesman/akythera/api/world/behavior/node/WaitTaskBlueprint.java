package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import net.minecraft.util.ExtraCodecs;

/// The parameters of a [WaitTask].
///
/// @param ticks how long the task runs, in server ticks
public record WaitTaskBlueprint(int ticks) implements BehaviorNodeBlueprint<WaitTask> {
    public static final MapCodec<WaitTaskBlueprint> CODEC = ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("ticks", 20)
        .xmap(WaitTaskBlueprint::new, WaitTaskBlueprint::ticks);

    @Override
    public WaitTask instantiate(final BrainComponent blueprintOwner) {
        return new WaitTask(ticks);
    }

    @Override
    public Class<WaitTask> nodeClass() {
        return WaitTask.class;
    }

    @Override
    public MapCodec<WaitTaskBlueprint> mapCodec() {
        return CODEC;
    }
}
