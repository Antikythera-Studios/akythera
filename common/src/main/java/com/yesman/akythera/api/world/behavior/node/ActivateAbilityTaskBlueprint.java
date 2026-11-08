package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;

/// The parameters of an [ActivateAbilityTask].
public record ActivateAbilityTaskBlueprint(AbilitySlot slot) implements BehaviorNodeBlueprint<ActivateAbilityTask> {
    public static final MapCodec<ActivateAbilityTaskBlueprint> CODEC = AbilitySlot.ENUM_MANAGER.getNameCodec()
        .fieldOf("slot")
        .xmap(ActivateAbilityTaskBlueprint::new, ActivateAbilityTaskBlueprint::slot);

    @Override
    public ActivateAbilityTask instantiate(final BrainComponent blueprintOwner) {
        return new ActivateAbilityTask(slot);
    }

    @Override
    public Class<ActivateAbilityTask> nodeClass() {
        return ActivateAbilityTask.class;
    }

    @Override
    public MapCodec<ActivateAbilityTaskBlueprint> mapCodec() {
        return CODEC;
    }
}
