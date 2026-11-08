package com.yesman.akythera.api.world.behavior.script;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.blueprint.behavior.BehaviorConditionBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;

/// A placement of the data pack condition `condition`.
public record DataDrivenBehaviorConditionBlueprint(ResourceKey<BehaviorConditionBlueprint> condition)
    implements BehaviorNodeBlueprint<DataDrivenBehaviorCondition>
{
    public static final MapCodec<DataDrivenBehaviorConditionBlueprint> CODEC = ResourceKey.codec(AkytheraRegistries.DataPack.BEHAVIOR_CONDITION_KEY)
        .fieldOf("condition")
        .xmap(DataDrivenBehaviorConditionBlueprint::new, DataDrivenBehaviorConditionBlueprint::condition);

    /// @throws IllegalStateException when the data pack holds no such condition
    @Override
    public DataDrivenBehaviorCondition instantiate(final BrainComponent blueprintOwner) {
        return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_CONDITION_KEY)
            .getOptional(condition)
            .orElseThrow(() -> new IllegalStateException("No behavior condition " + condition.identifier()))
            .instantiate(blueprintOwner);
    }

    @Override
    public Class<DataDrivenBehaviorCondition> nodeClass() {
        return DataDrivenBehaviorCondition.class;
    }

    @Override
    public MapCodec<DataDrivenBehaviorConditionBlueprint> mapCodec() {
        return CODEC;
    }
}
