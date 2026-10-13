package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.DataDrivenAbilityCue;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/// The data pack entry for a [DataDrivenAbilityCue]: the [BlueprintBody] authored on top of
/// [AbilityCueScript].
public record AbilityCueBlueprint(BlueprintBody body) implements Blueprint<DataDrivenAbilityCue, AbilityCue> {
    public static final MapCodec<AbilityCueBlueprint> MAP_CODEC =
        BlueprintBody.MAP_CODEC.xmap(AbilityCueBlueprint::new, AbilityCueBlueprint::body);

    public static final Codec<AbilityCueBlueprint> CODEC = MAP_CODEC.codec();

    /// Use [AbilitySystemComponent#playCue(ResourceKey, AbilityInstance, boolean, boolean)] to play the cue by
    /// its key
    @Override
    public DataDrivenAbilityCue instantiate(final AbilityCue blueprintOwner) {
        Registry<AbilityCueBlueprint> registry =
            StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ABILITY_CUE_KEY);

        return new DataDrivenAbilityCue((Holder.Reference<AbilityCueBlueprint>)registry.wrapAsHolder(this), blueprintOwner);
    }

    @Override
    public MapCodec<AbilityCueBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
