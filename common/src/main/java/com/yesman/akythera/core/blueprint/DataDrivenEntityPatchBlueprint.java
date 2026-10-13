package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.DataDrivenEntityPatchScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;

import java.util.Map;

/// An entity patch whose components and [BlueprintBody] are data, run by [DataDrivenEntityPatchScript].
public record DataDrivenEntityPatchBlueprint(
    Map<String, ComponentBlueprint<?, ?>> components,
    ScriptFunction ctor,
    BlueprintBody body
) implements EntityPatchBlueprint
{
    public static final MapCodec<DataDrivenEntityPatchBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES)
                    .fieldOf("components").forGetter(DataDrivenEntityPatchBlueprint::components),
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent").forGetter(DataDrivenEntityPatchBlueprint::ctor),
                BlueprintBody.MAP_CODEC.forGetter(DataDrivenEntityPatchBlueprint::body)
            ).apply(instance, DataDrivenEntityPatchBlueprint::new)
    );

    @Override
    @SuppressWarnings("unchecked")
    public DataDrivenEntityPatchScript instantiate(final EntityPatch<Entity> blueprintOwner) {
        Registry<EntityPatchBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY);
        return new DataDrivenEntityPatchScript((Holder.Reference<DataDrivenEntityPatchBlueprint>)(Holder<?>)registry.wrapAsHolder(this), blueprintOwner);
    }

    @Override
    public MapCodec<DataDrivenEntityPatchBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
