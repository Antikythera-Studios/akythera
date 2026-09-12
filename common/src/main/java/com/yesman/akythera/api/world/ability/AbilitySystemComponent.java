package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.TickableDynamicComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;

public final class AbilitySystemComponent
    implements DynamicPatchComponent<LivingEntityPatch<?>>, TickableDynamicComponent<LivingEntityPatch<?>>, UniqueComponent
{
    @Override
    public void tick() {

    }

    @Override
    public void frameUpdate(float frameTime, float partialTick) {

    }

    @Override
    public LivingEntityPatch<?> getOwner() {
        return null;
    }

    @Override
    public DynamicPatchComponent<LivingEntityPatch<?>> getRenewed(LivingEntityPatch<?> newOwner) {
        return null;
    }
}
