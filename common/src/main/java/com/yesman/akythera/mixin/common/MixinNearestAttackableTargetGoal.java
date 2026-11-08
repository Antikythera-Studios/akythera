package com.yesman.akythera.mixin.common;

import com.yesman.akythera.mixinaccessor.common.NearestAttackableTargetGoalAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NearestAttackableTargetGoal.class)
public abstract class MixinNearestAttackableTargetGoal<T extends LivingEntity> implements NearestAttackableTargetGoalAccessor {
    @Shadow
    @Final
    protected Class<T> targetType;

    @Shadow
    @Final
    protected TargetingConditions targetConditions;

    @Override
    public Class<?> akythera$getTargetType() {
        return targetType;
    }

    @Override
    public TargetingConditions akythera$getTargetConditions() {
        return targetConditions;
    }
}
