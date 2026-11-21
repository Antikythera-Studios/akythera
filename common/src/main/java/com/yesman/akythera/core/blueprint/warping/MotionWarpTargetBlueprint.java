package com.yesman.akythera.core.blueprint.warping;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.warping.MotionWarpTarget;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

/// What a motion warping window finds its destination with: a built-in [MotionWarpTarget], a Java class, or a data
/// pack entry. Dispatched on `type` through [AkytheraRegistries.Frozen#MOTION_WARP_TARGET_TYPES].
public interface MotionWarpTargetBlueprint {
    Codec<MotionWarpTargetBlueprint> CODEC = AkytheraRegistries.Frozen.MOTION_WARP_TARGET_TYPES
        .byNameCodec()
        .dispatch(MotionWarpTargetBlueprint::mapCodec, Function.identity());

    /// The target `owner` finds its destination with, for one warping window.
    ///
    /// @throws IllegalStateException when the target can't be built
    MotionWarpTarget instantiate(final EntityPatch<?> owner);

    MapCodec<? extends MotionWarpTargetBlueprint> mapCodec();

    /// A [MotionWarpTarget] class with a public no-argument constructor.
    record ByClass(ScriptType type) implements MotionWarpTargetBlueprint {
        public static final MapCodec<ByClass> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("class").forGetter(ByClass::type)
                ).apply(instance, ByClass::new)
        );

        @Override
        public MotionWarpTarget instantiate(final EntityPatch<?> owner) {
            if (!MotionWarpTarget.class.isAssignableFrom(type.type())) {
                throw new IllegalStateException(type.type().getSimpleName() + " is no " + MotionWarpTarget.class.getSimpleName());
            }

            try {
                return (MotionWarpTarget)type.type().getConstructor().newInstance();
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new IllegalStateException("Failed at building motion warp target " + type.type().getSimpleName(), e);
            }
        }

        @Override
        public MapCodec<ByClass> mapCodec() {
            return MAP_CODEC;
        }
    }

    /// A [MotionWarpTargetScriptBlueprint] data pack entry, or the [MotionWarpTarget] class registered under its id,
    /// which wins it ([ScriptType#classOf]).
    record ByData(ResourceKey<MotionWarpTargetScriptBlueprint> target) implements MotionWarpTargetBlueprint {
        public static final MapCodec<ByData> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    ResourceKey.codec(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY).fieldOf("target").forGetter(ByData::target)
                ).apply(instance, ByData::new)
        );

        @Override
        public MotionWarpTarget instantiate(final EntityPatch<?> owner) {
            ScriptType type = ScriptType.classOf(target, MotionWarpTarget.class);

            if (type != null) return new ByClass(type).instantiate(owner);

            return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY)
                .getOptional(target)
                .orElseThrow(() -> new IllegalStateException("No motion warp target " + target.identifier()))
                .instantiate(owner);
        }

        @Override
        public MapCodec<ByData> mapCodec() {
            return MAP_CODEC;
        }
    }
}
