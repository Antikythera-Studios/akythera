package com.yesman.akythera.core.blueprint.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.script.BehaviorConditionScript;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorCondition;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/// The data pack entry of a mob behavior condition: the [BlueprintBody] a [DataDrivenBehaviorCondition] runs, implementing [BehaviorConditionScript].
public record BehaviorConditionBlueprint(BlueprintBody body) implements Blueprint<DataDrivenBehaviorCondition, BrainComponent> {
    public static final MapCodec<BehaviorConditionBlueprint> MAP_CODEC =
        BlueprintBody.MAP_CODEC.xmap(BehaviorConditionBlueprint::new, BehaviorConditionBlueprint::body);

    public static final Codec<BehaviorConditionBlueprint> CODEC = MAP_CODEC.codec();

    /// The condition built for `blueprintOwner`.
    @Override
    @SuppressWarnings("unchecked")
    public DataDrivenBehaviorCondition instantiate(final BrainComponent blueprintOwner) {
        Registry<BehaviorConditionBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_CONDITION_KEY);

        return new DataDrivenBehaviorCondition((Holder.Reference<BehaviorConditionBlueprint>)registry.wrapAsHolder(this), blueprintOwner);
    }

    @Override
    public MapCodec<BehaviorConditionBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
