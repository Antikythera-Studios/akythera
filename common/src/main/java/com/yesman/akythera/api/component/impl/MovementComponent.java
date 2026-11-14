package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.UniqueComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/// Controls the transform of the [ComponentAttachable] it is attached to. [RootMotionComponent] moves its
/// owner through this.
///
/// Each kind of owner implements its own: an entity moves its [Entity], a particle its own coordinates.
///
/// [ComponentAttachable#getComponent] answers by the exact implementation type, so look one up with
/// [ComponentAttachable#findComponent].
public interface MovementComponent extends UniqueComponent {
    /// Where the owner is, in world space.
    Vec3 position();

    /// Where the owner faces, in Minecraft's yaw degrees.
    float yRot();

    /// Places the owner at `position`, in world space.
    void moveTo(final Vec3 position);

    /// Moves the owner by `delta`, in world space. The owner's own collision applies.
    void move(final Vec3 delta);

    /// Turns the owner to face `target`, in world space.
    void lookAt(final Vec3 target);

    /// Turns the owner to `xRot` and `yRot`, in degrees.
    void lookAt(final float xRot, final float yRot);

    /// Turns the owner by `yaw` degrees around the vertical axis, in Minecraft's yaw direction.
    void turn(final float yaw);

    /// `relative`, given in the owner's facing space (+Z forward), rotated into world space by the owner's
    /// yaw.
    Vec3 toWorldVector(final Vec3 relative);
}
