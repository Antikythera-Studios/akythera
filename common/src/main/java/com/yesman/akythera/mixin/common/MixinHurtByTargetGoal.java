package com.yesman.akythera.mixin.common;

import com.yesman.akythera.mixinaccessor.common.HurtByTargetGoalAccessor;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HurtByTargetGoal.class)
public abstract class MixinHurtByTargetGoal implements HurtByTargetGoalAccessor {
    @Shadow
    @Final
    private Class<?>[] toIgnoreDamage;

    @Override
    public Class<?>[] akythera$getToIgnoreDamage() {
        return toIgnoreDamage;
    }
}
