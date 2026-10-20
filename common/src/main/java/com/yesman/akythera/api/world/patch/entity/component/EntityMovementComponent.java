package com.yesman.akythera.api.world.patch.entity.component;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.impl.MovementComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/// [MovementComponent] of an entity patch: moves and turns the [EntityPatch#getOriginal] entity.
public final class EntityMovementComponent implements MovementComponent, DynamicPatchComponent<EntityPatch<?>> {
    private EntityPatch<?> owner;

    private boolean enabled = true;

    /// How the body yaw of a living owner turns.
    private final BodyRotation bodyRotation;

    public EntityMovementComponent(final EntityPatch<?> owner) {
        this(owner, BodyRotation.VANILLA);
    }

    public EntityMovementComponent(final EntityPatch<?> owner, final BodyRotation bodyRotation) {
        this.owner = owner;
        this.bodyRotation = bodyRotation;
    }

    public BodyRotation getBodyRotation() {
        return bodyRotation;
    }

    // ***************************************************************
    // MovementComponent
    // ***************************************************************

    @Override
    public void moveTo(final Vec3 position) {
        owner.getOriginal().setPos(position);
    }

    /// Keeps [Entity#onGround] through a level move.
    ///
    /// Vanilla [Entity#move] rewrites `onGround` on the side that controls the entity, and only a move
    /// with a downward part that hits a block sets it true. A root motion step is usually horizontal,
    /// so every step would set `onGround` false until the next tick's gravity move sets it true again,
    /// and an animator that reads `onGround` each frame would leave the ground states while the entity
    /// stands on the floor. A move up or down still lets vanilla decide.
    @Override
    public void move(final Vec3 delta) {
        Entity entity = owner.getOriginal();
        boolean onGround = entity.onGround();

        entity.move(MoverType.SELF, delta);

        // Entity#move grounds only a downward move, so a level step would read as airborne until the next tick
        if (onGround && delta.y == 0.0) entity.setOnGround(true);
    }

    @Override
    public void lookAt(final Vec3 target) {
        owner.getOriginal().lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    @Override
    public void lookAt(final float xRot, final float yRot) {
        owner.getOriginal().setXRot(xRot);
        owner.getOriginal().setYRot(yRot);
        owner.getOriginal().setYHeadRot(yRot);
    }

    @Override
    public Vec3 toWorldVector(final Vec3 relative) {
        return owner.fromRelativeToWorldVector(relative, false);
    }

    // ***************************************************************
    // DynamicPatchComponent
    // ***************************************************************

    @Override
    public EntityPatch<?> getOwner() {
        return owner;
    }

    @Override
    public EntityMovementComponent getRenewed(final EntityPatch<?> newOwner) {
        owner = newOwner;
        return this;
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

    public enum BodyRotation implements StringRepresentable {
        VANILLA("vanilla"), // turns toward the walking direction
        FACE_VIEW_WHILE_MOVING("face_view_while_moving"); // turns toward the view yaw while moving, so the legs can strafe

        public static final Codec<BodyRotation> CODEC = StringRepresentable.fromEnum(BodyRotation::values);

        private final String name;

        BodyRotation(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
