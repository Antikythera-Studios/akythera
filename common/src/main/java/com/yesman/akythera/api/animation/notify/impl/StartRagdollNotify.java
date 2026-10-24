package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;

/// Hands the owner's armature over to its rag doll for a while, the bodies moving on as the animation moved over the
/// last tick. Runs on both sides with nothing sent: the server keeps the time, which holds the entity's death and
/// interactions back, and each client simulates for that time. A death montage uses this, so a client never keeps a
/// body the server has already removed.
///
/// @see ArmatureComponent#startLocalRagdoll
public final class StartRagdollNotify extends AnimationNotify<ComponentAttachable> {
    public static final int DEFAULT_DURATION_TICKS = 100;

    public static final MapCodec<StartRagdollNotify> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("velocity_scale", 1.0F).forGetter(StartRagdollNotify::velocityScale),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("duration", DEFAULT_DURATION_TICKS).forGetter(StartRagdollNotify::durationTicks)
            ).apply(instance, StartRagdollNotify::new)
    );

    /// Multiplier on the velocity the bodies take from the animation.
    private final float velocityScale;

    /// How long the rag doll runs, in ticks.
    private final int durationTicks;

    public StartRagdollNotify(final float velocityScale, final int durationTicks) {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);

        this.velocityScale = velocityScale;
        this.durationTicks = durationTicks;
    }

    @Override
    public void onNotified(final ComponentAttachable owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
        ArmatureComponent armature = ArmatureComponent.armatureOf(owner);

        if (armature != null) armature.startLocalRagdoll(velocityScale, durationTicks);
    }

    public float velocityScale() {
        return velocityScale;
    }

    public int durationTicks() {
        return durationTicks;
    }

    @Override
    public MapCodec<? extends AnimationNotify<?>> mapCodec() {
        return CODEC;
    }
}
