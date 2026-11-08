package com.yesman.akythera.api.world.behavior.script;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.blueprint.behavior.BehaviorSubtaskBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;

/// A placement of the data pack subtask `subtask`.
public record DataDrivenBehaviorSubtaskBlueprint(ResourceKey<BehaviorSubtaskBlueprint> subtask) implements BehaviorNodeBlueprint<DataDrivenBehaviorSubtask> {
    public static final MapCodec<DataDrivenBehaviorSubtaskBlueprint> CODEC = ResourceKey.codec(AkytheraRegistries.DataPack.BEHAVIOR_SUBTASK_KEY)
        .fieldOf("subtask")
        .xmap(DataDrivenBehaviorSubtaskBlueprint::new, DataDrivenBehaviorSubtaskBlueprint::subtask);

    /// @throws IllegalStateException when the data pack holds no such subtask
    @Override
    public DataDrivenBehaviorSubtask instantiate(final BrainComponent blueprintOwner) {
        return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_SUBTASK_KEY)
            .getOptional(subtask)
            .orElseThrow(() -> new IllegalStateException("No behavior subtask " + subtask.identifier()))
            .instantiate(blueprintOwner);
    }

    @Override
    public Class<DataDrivenBehaviorSubtask> nodeClass() {
        return DataDrivenBehaviorSubtask.class;
    }

    @Override
    public MapCodec<DataDrivenBehaviorSubtaskBlueprint> mapCodec() {
        return CODEC;
    }
}
