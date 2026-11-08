package com.yesman.akythera.core.blueprint.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.script.BehaviorTaskScript;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorTask;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/// The data pack entry of a mob behavior task: the [BlueprintBody] a [DataDrivenBehaviorTask] runs, implementing [BehaviorTaskScript].
public record BehaviorTaskBlueprint(BlueprintBody body) implements Blueprint<DataDrivenBehaviorTask, BrainComponent> {
    public static final MapCodec<BehaviorTaskBlueprint> MAP_CODEC =
        BlueprintBody.MAP_CODEC.xmap(BehaviorTaskBlueprint::new, BehaviorTaskBlueprint::body);

    public static final Codec<BehaviorTaskBlueprint> CODEC = MAP_CODEC.codec();

    /// The task built for `blueprintOwner`.
    @Override
    @SuppressWarnings("unchecked")
    public DataDrivenBehaviorTask instantiate(final BrainComponent blueprintOwner) {
        Registry<BehaviorTaskBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_TASK_KEY);

        return new DataDrivenBehaviorTask((Holder.Reference<BehaviorTaskBlueprint>)registry.wrapAsHolder(this), blueprintOwner);
    }

    @Override
    public MapCodec<BehaviorTaskBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
