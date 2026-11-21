package com.yesman.akythera.api.world.patch.entity.component;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.impl.MovementComponent;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.mixinaccessor.common.LivingEntityAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// [MovementComponent] of an entity patch: moves and turns the [EntityPatch#getOriginal] entity, and runs its jump,
/// fall and swim rules through the owner's [EntityEventListener].
@ScriptType
public final class EntityMovementComponent implements MovementComponent, DynamicPatchComponent<EntityPatch<?>> {
    /// How far below its float height a floating owner may be for the float to hold it, in owner heights.
    private static final double FLOAT_REACH = 1.5;

    /// Part of the gap to the float height the owner rises or sinks each tick.
    private static final double FLOAT_GAIN = 0.3;

    /// Fastest the float moves the owner, in blocks per tick.
    private static final double FLOAT_MAX_SPEED = 0.1;

    /// How far above its float height the water may stand for the owner to count as floating, in blocks.
    private static final double FLOAT_DEPTH_MARGIN = 0.25;

    /// Fastest vertical speed, in blocks per tick, the float takes over from. Jumps, falls into the water and the climb
    /// out at a shore keep their momentum.
    private static final double FLOAT_TAKEOVER_SPEED = 0.25;

    /// Lowest the look may point, as the up part of the look vector, for a swimming owner to stay on the surface.
    private static final double SURFACE_SWIM_MIN_LOOK = -0.35;

    /// Ticks between two jumps from the ground, as vanilla waits.
    private static final int JUMP_DELAY_TICKS = 10;

    /// Most water blocks [#getWaterSurfaceDepth] looks up through.
    private static final int MAX_WATER_SCAN = 16;

    /// Speed, in blocks per tick, an owner that stands still walks off at where it faces, for [#getWaterDrop].
    private static final double WATER_DROP_STILL_SPEED = 0.2D;

    /// Most blocks [#getWaterDrop] follows a fall down.
    private static final int MAX_WATER_DROP = 64;

    /// Most ticks [#getWaterDrop] follows a fall for.
    private static final int MAX_WATER_DROP_TICKS = 100;

    /// Vanilla's vertical air drag per tick, which [#getWaterDrop] keeps while it drops the horizontal one.
    private static final double AIR_DRAG_Y = 0.98D;

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

    /// Part of its height a floating owner keeps under the water, 0 for vanilla swimming or an owner that can't float.
    private final float surfaceFloat;

    /// Whether the jump key was let go since the owner last jumped from the ground, see [#jumpRequiresPress].
    private boolean jumpReleased = true;

    /// Y at the previous tick, NaN before the first one.
    private double lastTickY = Double.NaN;

    /// Vertical speed over the last tick, in blocks per second.
    private float verticalSpeed;

    /// The highest Y since the owner left the ground, see [#getFallHeight].
    private double fallPeakY;

    /// The Y the owner last left the ground at, see [#getHeightAboveTakeoff].
    private double takeoffY;

    private float fallHeight;

    private boolean airborne;

    /// Whether the next travel keeps the owner's momentum, see [#keepMomentumInNextUpdate].
    private boolean keepMomentumInNextUpdate;

    /// The speed the current travel keeps, null when it keeps none.
    private @Nullable Vec3 heldSpeed;

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
        this.surfaceFloat = canFloatInWater ? surfaceFloat : 0.0F;

        listenOwnerEvents();
    }

    /// Registers the fall tracking, the jump rules, the surface float and the held momentum on the owner's
    /// [EntityEventListener].
    private void listenOwnerEvents() {
        EntityEventListener listener = owner.getEntityEventListener();

        listener.registerEvent(BuiltInEventHooks.Entity.TICK_POST, _ -> {
            if (!enabled) return;

            Entity entity = owner.getOriginal();
            double y = entity.getY();
            double previousY = Double.isNaN(lastTickY) ? y : lastTickY;
            boolean wasAirborne = airborne;
            airborne = !entity.onGround();
            verticalSpeed = (float)((y - previousY) * SharedConstants.TICKS_PER_SECOND);
            lastTickY = y;

            // Fluids, climbing, flight, riding and slow falling restart the fall where the owner is
            if (wasAirborne || airborne) {
                boolean restartsFall = entity.isInLiquid() || entity.isPassenger() || owner.isFlying()
                    || entity instanceof LivingEntity living && (living.onClimbable() || living.isFallFlying() || living.hasEffect(MobEffects.SLOW_FALLING));

                if (!wasAirborne) {
                    fallPeakY = Math.max(previousY, y);
                    takeoffY = previousY;
                }

                if (restartsFall) {
                    fallPeakY = y;
                    takeoffY = y;
                }

                fallPeakY = Math.max(fallPeakY, y);
                fallHeight = (float)(fallPeakY - y);
            }

            // Vanilla starts a swim only under the water, where a floating owner never gets its eyes
            if (!entity.isSwimming() && entity.isSprinting() && entity.isInWater() && !entity.isPassenger() && floatsInWater()) {
                entity.setSwimming(true);
            }
        });

        listener.registerEvent(BuiltInEventHooks.Entity.JUMP_INPUT, event -> {
            if (!enabled) return;

            LivingEntity entity = event.getEntityPatch().getOriginal();
            boolean fromGround = entity.onGround() && !entity.isInLiquid() && !entity.onClimbable();

            if (jumpRequiresPress && !event.isJumping()) jumpReleased = true;

            if (fromGround && jumpRequiresPress && !jumpReleased) event.setJumping(false);
        });

        listener.registerEvent(BuiltInEventHooks.Entity.JUMPED, event -> {
            if (!enabled) return;

            jumpReleased = false;

            LivingEntity entity = event.getEntityPatch().getOriginal();
            float inputLength = Mth.sqrt(entity.xxa * entity.xxa + entity.zza * entity.zza);

            if (!omnidirectionalSprint || inputLength < 1.0E-5F) return;

            // Aims the sprint boost along the movement input, not the view yaw
            float sin = Mth.sin(entity.getYRot() * Mth.DEG_TO_RAD);
            float cos = Mth.cos(entity.getYRot() * Mth.DEG_TO_RAD);
            Vec3 boost = event.getSprintBoost();
            double length = boost.horizontalDistance();
            float strafe = entity.xxa / inputLength;
            float forward = entity.zza / inputLength;

            event.setSprintBoost(new Vec3((strafe * cos - forward * sin) * length, boost.y, (forward * cos + strafe * sin) * length));
        });

        // Keeps the owner at its float height near the surface until it sneaks or looks down while swimming to dive.
        // Jumping against a shore rises as in vanilla to climb out, and a jump in water too shallow to float in is a
        // jump from the ground.
        listener.registerEvent(BuiltInEventHooks.Entity.TRAVEL_PRE, event -> {
            if (!enabled) return;

            LivingEntity entity = event.getEntityPatch().getOriginal();

            if (!entity.isInWater() || !entity.isAffectedByFluids() || entity.isPassenger()) return;
            if (entity.isShiftKeyDown() || entity.isSwimming() && entity.getLookAngle().y < SURFACE_SWIM_MIN_LOOK) return;
            if (entity.isJumping() && entity.horizontalCollision) return;

            double speed = surfaceFloatSpeed();

            if (Double.isNaN(speed)) return;

            if (entity.isJumping() && entity.onGround() && speed <= 0.0D) {
                LivingEntityAccessor accessor = (LivingEntityAccessor)entity;

                if (accessor.akythera$getNoJumpDelay() == 0) {
                    entity.jumpFromGround();
                    accessor.akythera$setNoJumpDelay(JUMP_DELAY_TICKS);
                }

                return;
            }

            Vec3 motion = entity.getDeltaMovement();

            if (Math.abs(motion.y) <= FLOAT_TAKEOVER_SPEED) entity.setDeltaMovement(motion.x, speed, motion.z);
        });

        // Drops the movement input and remembers the speed while the owner is asked to keep its momentum in the air
        listener.registerEvent(BuiltInEventHooks.Entity.TRAVEL_PRE, event -> {
            LivingEntity entity = event.getEntityPatch().getOriginal();
            boolean keepMomentum = keepMomentumInNextUpdate;

            keepMomentumInNextUpdate = false;
            heldSpeed = null;

            if (!enabled || !keepMomentum) return;
            if (entity.onGround() || entity.isInLiquid() || entity.isPassenger() || entity.isFallFlying() || owner.isFlying()) return;

            entity.xxa = 0.0F;
            entity.zza = 0.0F;
            heldSpeed = entity.getDeltaMovement();
        });

        // Gives back the horizontal speed the air drag took, unless the owner ran into a wall
        listener.registerEvent(BuiltInEventHooks.Entity.TRAVEL_POST, event -> {
            LivingEntity entity = event.getEntityPatch().getOriginal();

            if (heldSpeed == null || entity.horizontalCollision) return;

            entity.setDeltaMovement(heldSpeed.x, entity.getDeltaMovement().y, heldSpeed.z);
        });

        // Turns the body to face the way it flies
        listener.registerEvent(BuiltInEventHooks.Entity.BODY_TURN, event -> {
            if (heldSpeed == null || heldSpeed.horizontalDistanceSqr() < 1.0E-4D) return;

            event.getEntityPatch().getOriginal().yBodyRot = (float)(Mth.atan2(heldSpeed.z, heldSpeed.x) * Mth.RAD_TO_DEG) - 90.0F;
        });
    }

    /// Keeps the owner's horizontal speed and facing through the air in the next travel, and drops its movement input
    /// there, so a leap can't be steered, turned or stopped. Asked again every tick it should hold.
    public void keepMomentumInNextUpdate() {
        keepMomentumInNextUpdate = true;
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

    /// Vertical speed over the last tick, in blocks per second. Positive while rising.
    @ScriptFunction(pure = true, label = "Get Vertical Speed", desc = "Vertical speed over the last tick, in blocks per second. Positive while rising.")
    public float getVerticalSpeed() {
        return verticalSpeed;
    }

    /// Blocks dropped from the highest point since the owner left the ground. On the ground, the height of the fall
    /// that ended.
    @ScriptFunction(pure = true, label = "Get Fall Height", desc = "Blocks dropped from the highest point since the entity left the ground. On the ground, the height of the fall that ended.")
    public float getFallHeight() {
        return fallHeight;
    }

    /// Blocks the owner is above the height it last left the ground at, negative below it. 0 on the ground.
    @ScriptFunction(pure = true, label = "Get Height Above Takeoff", desc = "Blocks the entity is above the height it last left the ground at, negative below it. 0 on the ground.")
    public float getHeightAboveTakeoff() {
        return airborne ? (float)(owner.getOriginal().getY() - takeoffY) : 0.0F;
    }

    /// How far the surface of the water at the owner's feet is above them, in blocks. 0 out of water.
    @ScriptFunction(pure = true, label = "Get Water Surface Depth", desc = "How far the surface of the water at the entity's feet is above them, in blocks. 0 out of water.")
    public float getWaterSurfaceDepth() {
        Entity entity = owner.getOriginal();
        Level level = entity.level();
        BlockPos.MutableBlockPos pos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()).mutable();

        if (!level.getFluidState(pos).is(FluidTags.WATER)) return 0.0F;

        for (int i = 0; i < MAX_WATER_SCAN; i++) {
            if (!level.getFluidState(pos.move(Direction.UP)).is(FluidTags.WATER)) break;
        }

        pos.move(Direction.DOWN);

        return (float)(pos.getY() + level.getFluidState(pos).getHeight(level, pos) - entity.getY());
    }

    /// How far below the owner's feet lies the surface of the water its body would fall into, in blocks, when that
    /// water is at least `minDepth` blocks deep. The fall follows [EntityPatch#getVelocity] under gravity with no
    /// horizontal air drag. An owner that stands still walks off where it faces.
    ///
    /// @return -1 when the body would touch anything else first
    @ScriptFunction(pure = true, label = "Get Water Drop", desc = "How far below the entity's feet lies the surface of the water its body would fall into, following its velocity under gravity with no horizontal air drag, in blocks, when that water is at least Min Depth blocks deep. -1 when its body would touch anything else first. An entity that stands still walks off where it faces.")
    public float getWaterDrop(final int minDepth) {
        Entity entity = owner.getOriginal();
        Level level = entity.level();
        Vec3 velocity = owner.getVelocity();

        if (velocity.horizontalDistanceSqr() < 1.0E-4D) {
            Vec3 facing = Vec3.directionFromRotation(0.0F, entity.getYRot());

            velocity = new Vec3(facing.x * WATER_DROP_STILL_SPEED, 0.0D, facing.z * WATER_DROP_STILL_SPEED);
        }

        AABB body = entity.getBoundingBox().deflate(1.0E-4D);
        double dy = velocity.y;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int tick = 0; tick < MAX_WATER_DROP_TICKS && entity.getY() - body.minY < MAX_WATER_DROP; tick++) {
            AABB sweep = body.expandTowards(velocity.x, dy, velocity.z);
            AABB next = body.move(velocity.x, dy, velocity.z);
            Vec3 feet = new Vec3((next.minX + next.maxX) * 0.5D, next.minY, (next.minZ + next.maxZ) * 0.5D);

            for (int blockY = Mth.floor(body.minY); blockY >= Mth.floor(next.minY); blockY--) {
                pos.set(feet.x, blockY, feet.z);
                FluidState fluid = level.getFluidState(pos);

                if (!fluid.is(FluidTags.WATER)) continue;

                double surface = blockY + fluid.getHeight(level, pos);

                if (!level.noCollision(entity, sweep.setMinY(Math.max(sweep.minY, surface)))) return -1.0F;

                for (int depth = 1; depth < minDepth; depth++) {
                    if (!level.getFluidState(pos.below(depth)).is(FluidTags.WATER)) return -1.0F;
                }

                return (float)(entity.getY() - surface);
            }

            if (!level.noCollision(entity, sweep)) return -1.0F;

            body = next;
            dy = (dy - entity.getGravity()) * AIR_DRAG_Y;
        }

        return -1.0F;
    }

    /// The vertical speed, in blocks per tick, that eases the owner toward floating with [#surfaceFloat] of its height under
    /// the water, or NaN when it floats in no water or stands more than [#FLOAT_REACH] of its heights below that height.
    public double surfaceFloatSpeed() {
        float depth = getWaterSurfaceDepth();
        double gap = depth - owner.getBbHeight() * surfaceFloat;

        if (surfaceFloat <= 0.0F || depth <= 0.0F || gap > owner.getBbHeight() * FLOAT_REACH) return Double.NaN;

        return Mth.clamp(gap * FLOAT_GAIN, -FLOAT_MAX_SPEED, FLOAT_MAX_SPEED);
    }

    /// Whether the water is deep enough for the owner to float with [#surfaceFloat] of its height under it.
    public boolean floatsInWater() {
        return surfaceFloat > 0.0F && getWaterSurfaceDepth() >= owner.getBbHeight() * surfaceFloat - FLOAT_DEPTH_MARGIN;
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
    public Vec3 position() {
        return owner.getOriginal().position();
    }

    @Override
    public float yRot() {
        return owner.getOriginal().getYRot();
    }

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

    /// Turns the view, the head and the body together.
    @Override
    public void turn(final float yaw) {
        Entity entity = owner.getOriginal();

        entity.setYRot(entity.getYRot() + yaw);
        entity.setYHeadRot(entity.getYHeadRot() + yaw);

        if (entity instanceof LivingEntity livingEntity) livingEntity.setYBodyRot(livingEntity.yBodyRot + yaw);
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

    /// Registers the listeners again on `newOwner`, whose [EntityEventListener] starts empty.
    @Override
    public EntityMovementComponent getRenewed(final EntityPatch<?> newOwner) {
        owner = newOwner;
        listenOwnerEvents();

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
