package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;

/// Hands the owner's armature over to its rag doll at the window's start. Through the window the bodies collide while
/// they are pulled toward the animation, a pull that fades out until the rag doll moves on its own at the window's end.
///
/// Runs on both sides with nothing sent: the server keeps the time, which holds the entity's death and interactions
/// back, and each client simulates for that time. A death montage uses this, so a client never keeps a body the server
/// has already removed.
///
/// @see ArmatureComponent#startLocalRagdoll
public final class StartRagdollNotifyState extends AnimationNotifyState<ComponentAttachable> {
    public static final int DEFAULT_DURATION_TICKS = 100;

    /// One game tick, in the seconds of an animation's elapsed time.
    private static final float TICK_SECONDS = 0.05F;

    public static final MapCodec<StartRagdollNotifyState> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("velocity_scale", 1.0F).forGetter(StartRagdollNotifyState::velocityScale),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("duration", DEFAULT_DURATION_TICKS).forGetter(StartRagdollNotifyState::durationTicks)
            ).apply(instance, StartRagdollNotifyState::new)
    );

    /// Multiplier on the velocity the bodies take from the animation.
    private final float velocityScale;

    /// How long the rag doll runs from the window's start, in ticks.
    private final int durationTicks;

    public StartRagdollNotifyState(final float velocityScale, final int durationTicks) {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);

        this.velocityScale = velocityScale;
        this.durationTicks = durationTicks;
    }

    @Override
    public void onBegin(final ComponentAttachable owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
        ArmatureComponent armature = ArmatureComponent.armatureOf(owner);

        if (armature != null) {
            armature.startLocalRagdoll(velocityScale, durationTicks, Math.round((timeWindow.to() - timeWindow.from()) / TICK_SECONDS));
        }
    }

    @Override
    public void onTick(final ComponentAttachable owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void onEnd(final ComponentAttachable owner, final PlayableAnimation animation, final boolean interrupted) {
        ArmatureComponent armature = ArmatureComponent.armatureOf(owner);

        if (armature != null) armature.releaseRagdollDrive();
    }

    public float velocityScale() {
        return velocityScale;
    }

    public int durationTicks() {
        return durationTicks;
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
