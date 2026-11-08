package com.yesman.akythera.mixinaccessor.common;

import net.minecraft.world.entity.ai.targeting.TargetingConditions;

public interface NearestAttackableTargetGoalAccessor {
    Class<?> akythera$getTargetType();

    TargetingConditions akythera$getTargetConditions();
}
