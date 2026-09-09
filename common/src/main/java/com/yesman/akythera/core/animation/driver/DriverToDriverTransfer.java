package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.Pose;
import net.minecraft.world.entity.Entity;

/// Transit from a [AnimationDriver] to another [AnimationDriver]
///
/// This driver isn't exposed to script reflection system.
public final class DriverToDriverTransfer extends DriverTransfer {
    private final AnimationDriver from;

    public DriverToDriverTransfer(
        final AnimationDriver from, final AnimationDriver to, final float maxBlendTime,
        final Runnable onFinished
    ) {
        super(to, maxBlendTime, onFinished);

        this.from = from;
    }

    @Override
    public Pose getFromPose() {
        return from.getPose();
    }

    @Override
    public void update(final float frameTime) {
        from.update(frameTime);

        super.update(frameTime);
    }

    /// Drives the notify track of the animation this transfer is blending out of.
    public void tickFrom(final EntityPatch<Entity> owner) {
        from.tick(owner);
    }
}
