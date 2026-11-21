package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.world.ability.AbilityResourceScript;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.BlueprintExecutor;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;

/// The datapack entry for one [AbilityResourceScript]: the parent constructor, and the [BlueprintBody] authored on top
/// of the class it builds. [ComponentsFactory#getAbilityResource] builds it once per entry.
public record AbilityResourceBlueprint(ScriptFunction ctor, BlueprintBody body)
    implements Blueprint<BlueprintExecutor, AbilityResourceScript>
{
    public static final MapCodec<AbilityResourceBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent")
                    .forGetter(AbilityResourceBlueprint::ctor),
                BlueprintBody.MAP_CODEC.forGetter(AbilityResourceBlueprint::body)
            ).apply(instance, AbilityResourceBlueprint::new)
    );

    public static final Codec<AbilityResourceBlueprint> CODEC = MAP_CODEC.codec();

    /// What runs [#body] over `blueprintOwner`, a data-driven resource the parent constructor built.
    ///
    /// @throws IllegalArgumentException when `blueprintOwner` is no [DataDrivenScript]
    @Override
    public BlueprintExecutor instantiate(final AbilityResourceScript blueprintOwner) {
        if (!(blueprintOwner instanceof DataDrivenScript<?, ?> script)) {
            throw new IllegalArgumentException(blueprintOwner.getClass().getSimpleName() + " runs no blueprint");
        }

        return new BlueprintExecutor(script, ScriptModularizeHelper.scanBlueprintLayout(blueprintOwner.getClass()), body);
    }

    @Override
    public MapCodec<AbilityResourceBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
