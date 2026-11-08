package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;

/// Jumps in water and lava like [FloatGoal], while [EntityMovementComponent#canFloatInWater] allows it. Fails when
/// the owner is no [Mob] or is not in a liquid, and succeeds once it leaves one. Keeps no state, so every brain
/// shares one.
public record FloatTask() implements BehaviorTask, BehaviorNodeBlueprint<FloatTask> {
    public static final MapCodec<FloatTask> CODEC = MapCodec.unit(FloatTask::new);

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public TaskStatus doTask(final BrainComponent brain) {
        if (!(brain.getOwner().getOriginal() instanceof Mob mob) || !floats(brain, mob)) return TaskStatus.FAILED;

        mob.getNavigation().setCanFloat(true);

        if (mob.getRandom().nextFloat() < 0.8F) mob.getJumpControl().jump();

        return TaskStatus.RUNNING;
    }

    @Override
    public TaskStatus tickTask(final BrainComponent brain) {
        Mob mob = (Mob)brain.getOwner().getOriginal();

        if (!floats(brain, mob)) return TaskStatus.SUCCEEDED;

        if (mob.getRandom().nextFloat() < 0.8F) mob.getJumpControl().jump();

        return TaskStatus.RUNNING;
    }

    @Override
    public FloatTask instantiate(final BrainComponent blueprintOwner) {
        return this;
    }

    @Override
    public Class<FloatTask> nodeClass() {
        return FloatTask.class;
    }

    @Override
    public MapCodec<FloatTask> mapCodec() {
        return CODEC;
    }

    /// Whether `mob` is deep enough in a liquid to jump, the test of [FloatGoal#canUse], and may swim up.
    private static boolean floats(final BrainComponent brain, final Mob mob) {
        EntityMovementComponent movement = brain.getOwner().getComponent(EntityMovementComponent.class);

        if (movement != null && !movement.canFloatInWater()) return false;

        return mob.isInWater() && mob.getFluidHeight(FluidTags.WATER) > mob.getFluidJumpThreshold() || mob.isInLava();
    }
}
