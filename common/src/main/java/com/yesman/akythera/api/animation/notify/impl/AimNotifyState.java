package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;

/// Turns [#bone] up or down after where the owner looks while its window holds the playhead: by the owner's view
/// pitch times [#weight], at most [#maxPitch] degrees, in model space. The turn eases toward that over [#blendTime],
/// both when the window opens or closes and when the owner looks elsewhere.
///
/// [AnimationMontagePlayer] lays it on the montage's pose on both sides. The callbacks do nothing.
public final class AimNotifyState extends AnimationNotifyState<EntityPatch<?>> {
    public static final MapCodec<AimNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            Codec.STRING.fieldOf("bone").forGetter(AimNotifyState::bone),
            Codec.FLOAT.optionalFieldOf("weight", 1.0F).forGetter(AimNotifyState::weight),
            Codec.floatRange(0.0F, 180.0F).optionalFieldOf("max_pitch", 90.0F).forGetter(AimNotifyState::maxPitch),
            Codec.floatRange(0.0F, 10.0F).optionalFieldOf("blend_time", 0.2F).forGetter(AimNotifyState::blendTime)
        ).apply(inst, AimNotifyState::new)
    );

    /// The bone that turns, with everything under it.
    private final String bone;

    /// The share of the view pitch the bone turns by.
    private final float weight;

    /// The most the bone turns either way, in degrees.
    private final float maxPitch;

    /// How long the turn takes to get most of the way to a new target, in seconds. 0 follows at once.
    private final float blendTime;

    public AimNotifyState(final String bone, final float weight, final float maxPitch, final float blendTime) {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);

        this.bone = bone;
        this.weight = weight;
        this.maxPitch = maxPitch;
        this.blendTime = blendTime;
    }

    public String bone() {
        return bone;
    }

    public float weight() {
        return weight;
    }

    public float maxPitch() {
        return maxPitch;
    }

    public float blendTime() {
        return blendTime;
    }

    /// The turn for a view pitch of `pitch` degrees, in degrees.
    public float turn(final float pitch) {
        return Math.clamp(pitch * weight, -maxPitch, maxPitch);
    }

    @Override
    public void onBegin(final EntityPatch<?> owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
    }

    @Override
    public void onTick(final EntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void onEnd(final EntityPatch<?> owner, final PlayableAnimation animation, final boolean interrupted) {
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
