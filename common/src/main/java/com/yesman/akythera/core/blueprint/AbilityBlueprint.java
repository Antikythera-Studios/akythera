package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.DataDrivenAbilityState;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/// The datapack entry for one ability: the parent constructor, and the [BlueprintBody] authored on top of
/// the class it builds.
@ScriptType(autoRegister = false)
public record AbilityBlueprint(ScriptFunction ctor, BlueprintBody body)
    implements Blueprint<DataDrivenAbilityState, Ability<?>>
{
    public static final MapCodec<AbilityBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent")
                    .forGetter(AbilityBlueprint::ctor),
                BlueprintBody.MAP_CODEC.forGetter(AbilityBlueprint::body)
            ).apply(instance, AbilityBlueprint::new)
    );

    public static final Codec<AbilityBlueprint> CODEC = MAP_CODEC.codec();

    @Override
    public DataDrivenAbilityState instantiate(final Ability<?> blueprintOwner) {
        Registry<AbilityBlueprint> registry =
            StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ABILITY_KEY);

        return new DataDrivenAbilityState(
            (Holder.Reference<AbilityBlueprint>)registry.wrapAsHolder(this), blueprintOwner
        );
    }

    @Override
    public MapCodec<AbilityBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
