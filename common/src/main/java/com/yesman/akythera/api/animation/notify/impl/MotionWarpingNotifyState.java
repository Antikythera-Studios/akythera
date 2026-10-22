package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.phys.Vec3;

/// A notify state to rescale the root motion based on the start and destination points
///
/// Useful for rescaling attack motions that needs to follow the target
///
/// Since Root Motion based positioning is conducted on client side
public final class MotionWarpingNotifyState extends AnimationNotifyState<EntityPatch<?>> {
    public static final MapCodec<MotionWarpingNotifyState> CODEC =
        RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec()
                    .xmap(ScriptFunction::<EntityPatch<?>, Vec3>castToUniFunction, ScriptFunction::downcast)
                    .fieldOf("sound").forGetter(c -> c.positionProvider)
            ).apply(inst, MotionWarpingNotifyState::new)
        );

    private final ScriptFunction.ScriptUniFunction<EntityPatch<?>, Vec3> positionProvider;

    public MotionWarpingNotifyState(final ScriptFunction.ScriptUniFunction<EntityPatch<?>, Vec3> positionProvider) {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);
        this.positionProvider = positionProvider;
    }

    @Override
    public void onBegin(EntityPatch<?> owner, PlayableAnimation animation, TimeWindow timeWindow) {
    }

    @Override
    public void onTick(EntityPatch<?> owner, PlayableAnimation animation, float prevElapsedTime, float currentElapsedTime) {

    }

    @Override
    public void onEnd(EntityPatch<?> owner, PlayableAnimation animation, boolean interrupted) {

    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
