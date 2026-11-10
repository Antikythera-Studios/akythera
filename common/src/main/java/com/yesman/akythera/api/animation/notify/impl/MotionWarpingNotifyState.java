package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.animation.warping.AttackTargetWarpTarget;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/// Reshapes the root motion in its window so the owner reaches the destination [#target] finds: stretches the root's
/// movement by the distance left to the destination, and turns the owner to face it in place of the root's yaw.
///
/// The per-window state lives on the owner's [RootMotionComponent.Warp].
///
/// @param target          finds the destination
/// @param updateRate      ticks between two looks for the destination
/// @param warpTranslation whether the root's movement is stretched toward the destination
/// @param maxStretch      the largest multiplier of the root's movement
/// @param smooth          the largest change of the multiplier in one update, 0 for no limit
/// @param modifyX         whether the strafing (X) movement is stretched along with the forward (Z) one
/// @param modifyY         whether the vertical (Y) movement is stretched
/// @param warpRotation    whether the owner turns to face the destination
/// @param maxRotationRate the largest turn in one tick, in degrees
public final class MotionWarpingNotifyState extends AnimationNotifyState<EntityPatch<?>> {
    public static final MapCodec<MotionWarpingNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            MotionWarpTargetBlueprint.CODEC.optionalFieldOf("target", AttackTargetWarpTarget.INSTANCE).forGetter(MotionWarpingNotifyState::target),
            Codec.INT.optionalFieldOf("update_rate", 1).forGetter(MotionWarpingNotifyState::updateRate),
            Codec.BOOL.optionalFieldOf("warp_translation", true).forGetter(MotionWarpingNotifyState::warpTranslation),
            Codec.FLOAT.optionalFieldOf("max_stretch", 2.0F).forGetter(MotionWarpingNotifyState::maxStretch),
            Codec.FLOAT.optionalFieldOf("smooth", 0.0F).forGetter(MotionWarpingNotifyState::smooth),
            Codec.BOOL.optionalFieldOf("modify_x", true).forGetter(MotionWarpingNotifyState::modifyX),
            Codec.BOOL.optionalFieldOf("modify_y", false).forGetter(MotionWarpingNotifyState::modifyY),
            Codec.BOOL.optionalFieldOf("warp_rotation", true).forGetter(MotionWarpingNotifyState::warpRotation),
            Codec.FLOAT.optionalFieldOf("max_rotation_rate", 30.0F).forGetter(MotionWarpingNotifyState::maxRotationRate)
        ).apply(inst, MotionWarpingNotifyState::new)
    );

    private final MotionWarpTargetBlueprint target;
    private final int updateRate;
    private final boolean warpTranslation;
    private final float maxStretch;
    private final float smooth;
    private final boolean modifyX;
    private final boolean modifyY;
    private final boolean warpRotation;
    private final float maxRotationRate;

    public MotionWarpingNotifyState(
        final MotionWarpTargetBlueprint target, final int updateRate, final boolean warpTranslation, final float maxStretch,
        final float smooth, final boolean modifyX, final boolean modifyY, final boolean warpRotation, final float maxRotationRate
    ) {
        super(LogicalSide.BOTH, NotifyTriggerThread.WORLD);
        this.target = target;
        this.updateRate = updateRate;
        this.warpTranslation = warpTranslation;
        this.maxStretch = maxStretch;
        this.smooth = smooth;
        this.modifyX = modifyX;
        this.modifyY = modifyY;
        this.warpRotation = warpRotation;
        this.maxRotationRate = maxRotationRate;
    }

    public MotionWarpTargetBlueprint target() {
        return target;
    }

    public int updateRate() {
        return updateRate;
    }

    public boolean warpTranslation() {
        return warpTranslation;
    }

    public float maxStretch() {
        return maxStretch;
    }

    public float smooth() {
        return smooth;
    }

    public boolean modifyX() {
        return modifyX;
    }

    public boolean modifyY() {
        return modifyY;
    }

    public boolean warpRotation() {
        return warpRotation;
    }

    public float maxRotationRate() {
        return maxRotationRate;
    }

    @Override
    public void onBegin(final EntityPatch<?> owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
        ArmatureComponent armature = owner.getArmatureComponent();
        RootMotionComponent rootMotion = armature == null ? null : armature.getComponent(RootMotionComponent.class);

        if (rootMotion == null) return;

        try {
            rootMotion.startWarp(this, target.instantiate(owner), timeWindow.to());
        } catch (IllegalStateException e) {
            Akythera.LOGGER.error("Failed at starting motion warping", e);
        }
    }

    @Override
    public void onTick(final EntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
        ArmatureComponent armature = owner.getArmatureComponent();
        RootMotionComponent rootMotion = armature == null ? null : armature.getComponent(RootMotionComponent.class);
        RootMotionComponent.Warp warp = rootMotion == null ? null : rootMotion.getWarp();

        if (warp == null || warp.settings() != this) return;

        if (warp.tickUpdate()) warp.update(warp.target().find(owner), owner.position());

        Vec3 destination = warp.destination();
        Entity entity = owner.getOriginal();

        // Turns where the rotation is decided: a player on its own client, any other entity on the server
        boolean authority = owner.isLogicalClient() ? owner.isLocalPlayer() : !(entity instanceof Player);

        if (!warp.turns() || !authority) return;

        Vec3 gap = destination.subtract(entity.position());

        if (gap.horizontalDistanceSqr() < 1.0E-6D) return;

        float yRot = Mth.approachDegrees(entity.getYRot(), (float)(Mth.atan2(gap.z, gap.x) * Mth.RAD_TO_DEG) - 90.0F, maxRotationRate);

        entity.setYRot(yRot);
        entity.setYHeadRot(yRot);

        // Root motion follows the body yaw
        if (entity instanceof LivingEntity livingEntity) livingEntity.setYBodyRot(yRot);
    }

    @Override
    public void onEnd(final EntityPatch<?> owner, final PlayableAnimation animation, final boolean interrupted) {
        ArmatureComponent armature = owner.getArmatureComponent();
        RootMotionComponent rootMotion = armature == null ? null : armature.getComponent(RootMotionComponent.class);

        if (rootMotion != null) rootMotion.stopWarp(this);
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}
