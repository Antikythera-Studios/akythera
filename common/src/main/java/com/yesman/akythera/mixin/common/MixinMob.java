package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import com.yesman.akythera.mixinaccessor.common.MobAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Mob.class)
public abstract class MixinMob implements MobAccessor {
    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Shadow
    @Final
    protected GoalSelector targetSelector;

    @Override
    public GoalSelector akythera$getTargetSelector() {
        return targetSelector;
    }

    /// Runs the [BrainComponent] in place of the full tick of both goal selectors.
    @Redirect(
        method = "serverAiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V")
    )
    private void akythera$tickBrain(final GoalSelector selector) {
        BrainComponent brain = akythera$getBrain();

        if (brain == null) {
            selector.tick();
        } else if (selector == goalSelector) {
            brain.tick(true);
        }
    }

    /// Runs the [BrainComponent] in place of the goals both goal selectors tick between two full ticks.
    @Redirect(
        method = "serverAiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V")
    )
    private void akythera$tickBrainBetweenFullTicks(final GoalSelector selector, final boolean tickAllRunning) {
        BrainComponent brain = akythera$getBrain();

        if (brain == null) {
            selector.tickRunningGoals(tickAllRunning);
        } else if (selector == goalSelector) {
            brain.tick(false);
        }
    }

    @Unique
    private @Nullable BrainComponent akythera$getBrain() {
        return ((EntityAccessor)this).akythera$getEntityPatch() instanceof EntityPatch<?> patch
            ? patch.getComponent(BrainComponent.class)
            : null;
    }
}
