package com.yesman.akythera.api.world.behavior.script;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTaskBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;

/// A placement of the data pack task `task`.
public record DataDrivenBehaviorTaskBlueprint(ResourceKey<BehaviorTaskBlueprint> task) implements BehaviorNodeBlueprint<DataDrivenBehaviorTask> {
    public static final MapCodec<DataDrivenBehaviorTaskBlueprint> CODEC = ResourceKey.codec(AkytheraRegistries.DataPack.BEHAVIOR_TASK_KEY)
        .fieldOf("task")
        .xmap(DataDrivenBehaviorTaskBlueprint::new, DataDrivenBehaviorTaskBlueprint::task);

    /// @throws IllegalStateException when the data pack holds no such task
    @Override
    public DataDrivenBehaviorTask instantiate(final BrainComponent blueprintOwner) {
        return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_TASK_KEY)
            .getOptional(task)
            .orElseThrow(() -> new IllegalStateException("No behavior task " + task.identifier()))
            .instantiate(blueprintOwner);
    }

    @Override
    public Class<DataDrivenBehaviorTask> nodeClass() {
        return DataDrivenBehaviorTask.class;
    }

    @Override
    public MapCodec<DataDrivenBehaviorTaskBlueprint> mapCodec() {
        return CODEC;
    }
}
