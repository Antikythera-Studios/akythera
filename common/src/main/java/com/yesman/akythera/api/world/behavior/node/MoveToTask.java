package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.BlackboardKey;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Walks to [#destination] through the vanilla [PathNavigation]. Succeeds within [#acceptanceRadius], and fails
/// when no path is found or the owner is no [Mob].
public final class MoveToTask implements BehaviorTask {
    /// How far a moving destination may get from the end of the current path before a new path is made, in blocks.
    private static final double REPATH_DISTANCE = 1.0D;

    private final Destination destination;

    /// The distance at which the task succeeds, in blocks.
    private final float acceptanceRadius;

    /// The speed passed to [PathNavigation#moveTo].
    private final double speedModifier;

    /// The destination the current path was made for.
    private Vec3 pathGoal = Vec3.ZERO;

    /// Updates left before a moving destination gets a new path.
    private int repathDelay;

    public MoveToTask(final Destination destination, final float acceptanceRadius, final double speedModifier) {
        this.destination = destination;
        this.acceptanceRadius = acceptanceRadius;
        this.speedModifier = speedModifier;
    }

    @Override
    public TaskStatus doTask(final BrainComponent brain) {
        if (!(brain.getOwner().getOriginal() instanceof Mob mob)) return TaskStatus.FAILED;

        Vec3 goal = destination.position(brain);

        if (goal == null) return TaskStatus.FAILED;
        if (mob.position().distanceToSqr(goal) <= acceptanceRadius * acceptanceRadius) return TaskStatus.SUCCEEDED;

        return moveTo(mob, goal) ? TaskStatus.RUNNING : TaskStatus.FAILED;
    }

    @Override
    public TaskStatus tickTask(final BrainComponent brain) {
        Mob mob = (Mob)brain.getOwner().getOriginal();
        Vec3 goal = destination.position(brain);

        if (goal == null) {
            mob.getNavigation().stop();
            return TaskStatus.FAILED;
        }

        if (mob.position().distanceToSqr(goal) <= acceptanceRadius * acceptanceRadius) {
            mob.getNavigation().stop();
            return TaskStatus.SUCCEEDED;
        }

        if (!(destination instanceof Destination.Location)) {
            repathDelay--;

            boolean moved = goal.distanceToSqr(pathGoal) > REPATH_DISTANCE * REPATH_DISTANCE;

            if (repathDelay <= 0 && moved || mob.getNavigation().isDone()) {
                return moveTo(mob, goal) ? TaskStatus.RUNNING : TaskStatus.FAILED;
            }
        } else if (mob.getNavigation().isDone()) {
            return TaskStatus.FAILED;
        }

        return TaskStatus.RUNNING;
    }

    @Override
    public void onCanceled(final BrainComponent brain) {
        if (brain.getOwner().getOriginal() instanceof Mob mob) mob.getNavigation().stop();
    }

    /// Starts a path to `goal`, and waits 4 to 10 ticks before the next one.
    ///
    /// @return whether a path was found
    private boolean moveTo(final Mob mob, final Vec3 goal) {
        Path path = mob.getNavigation().createPath(goal.x, goal.y, goal.z, 0);

        if (path == null || !mob.getNavigation().moveTo(path, speedModifier)) return false;

        pathGoal = goal;
        repathDelay = adjustedTickDelay(4 + mob.getRandom().nextInt(7));

        return true;
    }

    /// Where a [MoveToTask] walks.
    public sealed interface Destination {
        Codec<Destination> CODEC = Codec.STRING.dispatch(
            destination ->
                switch (destination) {
                    case Target _ -> "target";
                    case Location _ -> "location";
                    case Blackboard _ -> "blackboard";
                },
            literal ->
                switch (literal) {
                    case "target" -> Target.CODEC;
                    case "location" -> Location.CODEC;
                    case "blackboard" -> Blackboard.CODEC;
                    default -> throw new IllegalArgumentException("Unsupported destination type " + literal);
                }
        );

        /// The position to walk to, or null when there is none.
        @Nullable Vec3 position(final BrainComponent brain);

        /// The owner's [EntityPatch#getTarget].
        record Target() implements Destination {
            static final MapCodec<Target> CODEC = MapCodec.unit(Target::new);

            @Override
            public @Nullable Vec3 position(final BrainComponent brain) {
                LivingEntity target = brain.getOwner().getTarget();

                return target == null || target.isRemoved() ? null : target.position();
            }
        }

        /// The entity or the position a blackboard variable holds.
        record Blackboard(BlackboardKey key) implements Destination {
            static final MapCodec<Blackboard> CODEC = BlackboardKey.CODEC.fieldOf("key").xmap(Blackboard::new, Blackboard::key);

            @Override
            public @Nullable Vec3 position(final BrainComponent brain) {
                return switch (brain.getBlackboardValue(key)) {
                    case Entity entity when !entity.isRemoved() -> entity.position();
                    case Vec3 position -> position;
                    case null, default -> null;
                };
            }
        }

        /// A fixed position.
        record Location(Vec3 location) implements Destination {
            static final MapCodec<Location> CODEC = Vec3.CODEC.fieldOf("location").xmap(Location::new, Location::location);

            @Override
            public Vec3 position(final BrainComponent brain) {
                return location;
            }
        }
    }
}
