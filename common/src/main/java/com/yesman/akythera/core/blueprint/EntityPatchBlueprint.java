package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import net.minecraft.world.entity.Entity;

/// How an entity patch is built: the [EntityPatch] subclass to construct, and the [EntityPatchScript]
/// it runs. Dispatched on `type`.
public sealed interface EntityPatchBlueprint extends Blueprint<EntityPatchScript, EntityPatch<Entity>>
    permits DataDrivenEntityPatchBlueprint, SourceCodeEntityPatchBlueprint
{
    Codec<EntityPatchBlueprint> CODEC = Codec.STRING.dispatch(
        "type",
        blueprint ->
            switch (blueprint) {
                case DataDrivenEntityPatchBlueprint _ -> "data_driven";
                case SourceCodeEntityPatchBlueprint _ -> "source_code_script";
            },
        literal ->
            switch (literal) {
                case "data_driven" -> DataDrivenEntityPatchBlueprint.MAP_CODEC;
                case "source_code_script" -> SourceCodeEntityPatchBlueprint.MAP_CODEC;
                default -> throw new IllegalArgumentException("Unsupported entity patch blueprint type " + literal);
            }
    );

    /// The [ScriptFunction.Usage#ENTITY_PATCH_CTOR] function the patch is built with.
    ScriptFunction ctor();

    @Override
    MapCodec<? extends EntityPatchBlueprint> mapCodec();
}
