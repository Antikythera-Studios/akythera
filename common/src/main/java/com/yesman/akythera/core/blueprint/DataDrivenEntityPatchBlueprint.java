package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.DataDrivenEntityPatchScript;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;

/// An entity patch whose components and [BlueprintBody] are data, run by [DataDrivenEntityPatchScript].
///
/// @param parentScript the source code [EntityPatchScript] the data is laid over, a class
///                     [SourceCodeEntityPatchBlueprint#runnable] accepts
/// @param dataParent   the entity patch whose components and body this one's are laid over
public record DataDrivenEntityPatchBlueprint(
    Map<String, ComponentBlueprint<?, ?>> components,
    ScriptFunction ctor,
    Optional<ScriptType> parentScript,
    Optional<Identifier> dataParent,
    BlueprintBody body
) implements EntityPatchBlueprint
{
    public static final MapCodec<DataDrivenEntityPatchBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES)
                    .fieldOf("components").forGetter(DataDrivenEntityPatchBlueprint::components),
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent").forGetter(DataDrivenEntityPatchBlueprint::ctor),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                    .validate(type -> SourceCodeEntityPatchBlueprint.runnable(type.type())
                        ? DataResult.success(type)
                        : DataResult.error(() -> type.type().getSimpleName() + " is not an " + EntityPatchScript.class.getSimpleName()
                            + " with a public constructor taking " + EntityPatch.class.getSimpleName()))
                    .optionalFieldOf("parent_script").forGetter(DataDrivenEntityPatchBlueprint::parentScript),
                Identifier.CODEC.optionalFieldOf("data_parent").forGetter(DataDrivenEntityPatchBlueprint::dataParent),
                BlueprintBody.MAP_CODEC.forGetter(DataDrivenEntityPatchBlueprint::body)
            ).apply(instance, DataDrivenEntityPatchBlueprint::new)
    );

    /// A blueprint laid over no parent.
    public DataDrivenEntityPatchBlueprint(
        final Map<String, ComponentBlueprint<?, ?>> components, final ScriptFunction ctor, final BlueprintBody body
    ) {
        this(components, ctor, Optional.empty(), Optional.empty(), body);
    }

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
