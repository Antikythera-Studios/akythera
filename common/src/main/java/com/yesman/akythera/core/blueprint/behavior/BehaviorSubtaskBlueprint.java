package com.yesman.akythera.core.blueprint.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.script.BehaviorSubtaskScript;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorSubtask;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/// The data pack entry of a mob behavior subtask: the [BlueprintBody] a [DataDrivenBehaviorSubtask] runs, implementing [BehaviorSubtaskScript].
public record BehaviorSubtaskBlueprint(BlueprintBody body) implements Blueprint<DataDrivenBehaviorSubtask, BrainComponent> {
    public static final MapCodec<BehaviorSubtaskBlueprint> MAP_CODEC =
        BlueprintBody.MAP_CODEC.xmap(BehaviorSubtaskBlueprint::new, BehaviorSubtaskBlueprint::body);

    public static final Codec<BehaviorSubtaskBlueprint> CODEC = MAP_CODEC.codec();

    /// The subtask built for `blueprintOwner`.
    @Override
    @SuppressWarnings("unchecked")
    public DataDrivenBehaviorSubtask instantiate(final BrainComponent blueprintOwner) {
        Registry<BehaviorSubtaskBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_SUBTASK_KEY);

        return new DataDrivenBehaviorSubtask((Holder.Reference<BehaviorSubtaskBlueprint>)registry.wrapAsHolder(this), blueprintOwner);
    }

    @Override
    public MapCodec<BehaviorSubtaskBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
