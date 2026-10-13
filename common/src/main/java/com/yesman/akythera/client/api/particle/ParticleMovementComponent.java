package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.api.component.impl.MovementComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// [MovementComponent] of a mesh particle: moves the particle and turns its model. The model's facing direction is
/// its +Z axis.
public final class ParticleMovementComponent implements MovementComponent {
    private final AkytheraMeshParticle particle;

    private boolean enabled = true;

    public ParticleMovementComponent(final AkytheraMeshParticle particle) {
        this.particle = particle;
    }

    // ***************************************************************
    // MovementComponent
    // ***************************************************************

    @Override
    public void moveTo(final Vec3 position) {
        particle.setPosition(position);
    }

    @Override
    public void move(final Vec3 delta) {
        particle.move(delta.x, delta.y, delta.z);
    }

    @Override
    public void lookAt(final Vec3 target) {
        Vec3 direction = target.subtract(particle.getPosition());

        lookAt(
            (float)(Mth.atan2(-direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG),
            (float)(Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG)
        );
    }

    /// Turns the model's +Z axis to Minecraft's facing for `xRot` (pitch, down positive) and `yRot` (yaw).
    @Override
    public void lookAt(final float xRot, final float yRot) {
        particle.setRotation(new Quaternionf().rotationYXZ(-yRot * Mth.DEG_TO_RAD, xRot * Mth.DEG_TO_RAD, 0.0F));
    }

    @Override
    public Vec3 toWorldVector(final Vec3 relative) {
        Vector3f rotated = particle.getRotation().transform(new Vector3f((float)relative.x, (float)relative.y, (float)relative.z));

        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
