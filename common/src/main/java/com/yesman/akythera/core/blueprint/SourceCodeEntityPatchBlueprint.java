package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.InvocationTargetException;

/// An entity patch run by a hand-written [EntityPatchScript], a class registered in
/// [AkytheraRegistries.Frozen#SCRIPT_TYPE]. See [#runnable].
///
/// @param script the [ScriptType] of the script class
public record SourceCodeEntityPatchBlueprint(ScriptFunction ctor, ScriptType script) implements EntityPatchBlueprint {
    public static final MapCodec<SourceCodeEntityPatchBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("parent").forGetter(SourceCodeEntityPatchBlueprint::ctor),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
                    .validate(type -> runnable(type.type())
                        ? DataResult.success(type)
                        : DataResult.error(() -> type.type().getSimpleName() + " is not an " + EntityPatchScript.class.getSimpleName()
                            + " with a public constructor taking " + EntityPatch.class.getSimpleName()))
                    .fieldOf("script").forGetter(SourceCodeEntityPatchBlueprint::script)
            ).apply(instance, SourceCodeEntityPatchBlueprint::new)
    );

    /// Whether `type` can run as a source code script: an [EntityPatchScript] declaring a public
    /// constructor that takes its owner [EntityPatch].
    public static boolean runnable(final Class<?> type) {
        if (!EntityPatchScript.class.isAssignableFrom(type)) return false;

        try {
            type.getConstructor(EntityPatch.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    public EntityPatchScript instantiate(final EntityPatch<Entity> blueprintOwner) {
        try {
            return (EntityPatchScript)script.type().getConstructor(EntityPatch.class).newInstance(blueprintOwner);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Failed at binding entity patch and script " + script.type().getSimpleName(), e);
        }
    }

    @Override
    public MapCodec<SourceCodeEntityPatchBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
