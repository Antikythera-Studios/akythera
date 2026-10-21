package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/// A notify state that ignores the owner's own movement (walking, jumping) in its time window. Root motion still
/// moves the owner.
///
/// Read through [AnimationMontagePlayer#getActiveNotifyState] every tick, so the callbacks do nothing.
public final class LockMovementNotifyState extends AnimationNotifyState<LivingEntityPatch<?>> {
    public static final MapCodec<LockMovementNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            Rotation.CODEC.optionalFieldOf("rotation", Rotation.FACE_VIEW).forGetter(LockMovementNotifyState::rotation)
        ).apply(inst, LockMovementNotifyState::new)
    );

    /// How the owner's body yaw behaves in the window
    private final Rotation rotation;

    public LockMovementNotifyState(final Rotation rotation) {
        super(LogicalSide.BOTH);
        this.rotation = rotation;
    }

    public Rotation rotation() {
        return rotation;
    }

    @Override
    public void onBegin(final LivingEntityPatch<?> owner, final PlayableAnimation animation) {
    }

    @Override
    public void onTick(final LivingEntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void onEnd(final LivingEntityPatch<?> owner, final PlayableAnimation animation) {
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }

    public enum Rotation implements StringRepresentable {
        FREE("free"), // turns as usual
        FACE_VIEW("face_view"), // body yaw follows the view yaw
        LOCKED("locked"); // body yaw stays where it was when the lock began

        public static final Codec<Rotation> CODEC = StringRepresentable.fromEnum(Rotation::values);

        private final String name;

        Rotation(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
