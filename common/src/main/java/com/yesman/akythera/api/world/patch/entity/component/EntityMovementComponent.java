package com.yesman.akythera.api.world.patch.entity.component;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.impl.MovementComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/// [MovementComponent] of an entity patch: moves and turns the [EntityPatch#getOriginal] entity.
public final class EntityMovementComponent implements MovementComponent, DynamicPatchComponent<EntityPatch<?>> {
    /// How far below its float height a floating owner may be for the float to hold it, in owner heights.
    private static final double FLOAT_REACH = 1.5;

    /// Part of the gap to the float height the owner rises or sinks each tick.
    private static final double FLOAT_GAIN = 0.3;

    /// Fastest the float moves the owner, in blocks per tick.
    private static final double FLOAT_MAX_SPEED = 0.1;

    /// How far above its float height the water may stand for the owner to count as floating, in blocks.
    private static final double FLOAT_DEPTH_MARGIN = 0.25;

    private EntityPatch<?> owner;

    private boolean enabled = true;

    /// How the body yaw of a living owner turns.
    private final BodyRotation bodyRotation;

    /// Whether the owner can swim up in water at all.
    private final boolean canFloatInWater;

    /// Whether a player owner may sprint in any direction it moves, not only forward.
    private final boolean omnidirectionalSprint;

    /// Whether each jump from the ground needs the jump key pressed again since the last jump, so holding it never repeats.
    private final boolean jumpRequiresPress;

    /// Part of its height a floating owner keeps under the water, 0 for vanilla swimming.
    private final float surfaceFloat;

    public EntityMovementComponent(final EntityPatch<?> owner) {
        this(owner, BodyRotation.VANILLA, true, false, false, 0.0F);
    }

    public EntityMovementComponent(
        final EntityPatch<?> owner, final BodyRotation bodyRotation, final boolean canFloatInWater, final boolean omnidirectionalSprint,
        final boolean jumpRequiresPress, final float surfaceFloat
    ) {
        this.owner = owner;
        this.bodyRotation = bodyRotation;
        this.canFloatInWater = canFloatInWater;
        this.omnidirectionalSprint = omnidirectionalSprint;
        this.jumpRequiresPress = jumpRequiresPress;
        this.surfaceFloat = surfaceFloat;
    }

    public BodyRotation getBodyRotation() {
        return bodyRotation;
    }

    public boolean canFloatInWater() {
        return canFloatInWater;
    }

    public boolean isOmnidirectionalSprint() {
        return omnidirectionalSprint;
    }

    public boolean jumpRequiresPress() {
        return jumpRequiresPress;
    }

    public float surfaceFloat() {
        return surfaceFloat;
    }

    /// The vertical speed, in blocks per tick, that eases the owner toward floating with [#surfaceFloat] of its height under
    /// the water, or NaN when it floats in no water or stands more than [#FLOAT_REACH] of its heights below that height.
    public double surfaceFloatSpeed() {
        float depth = owner.getWaterSurfaceDepth();
        double gap = depth - owner.getBbHeight() * surfaceFloat;

        if (surfaceFloat <= 0.0F || depth <= 0.0F || gap > owner.getBbHeight() * FLOAT_REACH) return Double.NaN;

        return Mth.clamp(gap * FLOAT_GAIN, -FLOAT_MAX_SPEED, FLOAT_MAX_SPEED);
    }

    /// Whether the water is deep enough for the owner to float with [#surfaceFloat] of its height under it.
    public boolean floatsInWater() {
        return surfaceFloat > 0.0F && owner.getWaterSurfaceDepth() >= owner.getBbHeight() * surfaceFloat - FLOAT_DEPTH_MARGIN;
    }

    /// Whether `entity` has an enabled movement component that floats it in the water it stands in, see [#floatsInWater()].
    public static boolean floatsInWater(final Entity entity) {
        EntityPatch<?> patch = AkytheraPatches.getEntityPatch(entity);
        EntityMovementComponent movement = patch == null ? null : patch.getComponent(EntityMovementComponent.class);

        return movement != null && movement.isEnabled() && movement.floatsInWater();
    }

    /// Whether `entity` has an enabled movement component that lets it sprint in any direction.
    public static boolean canSprintInAnyDirection(final Entity entity) {
        EntityPatch<?> patch = AkytheraPatches.getEntityPatch(entity);
        EntityMovementComponent movement = patch == null ? null : patch.getComponent(EntityMovementComponent.class);

        return movement != null && movement.isEnabled() && movement.omnidirectionalSprint;
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
