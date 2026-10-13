package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

/// One entry of an emitter's updater list: a built-in module with its parameters, a Java class, or a data pack entry.
/// Dispatched on `type` through [AkytheraRegistries.Frozen#PARTICLE_UPDATER_TYPES].
public interface ParticleUpdaterBlueprint {
    Codec<ParticleUpdaterBlueprint> CODEC = AkytheraRegistries.Frozen.PARTICLE_UPDATER_TYPES
        .byNameCodec()
        .dispatch(ParticleUpdaterBlueprint::mapCodec, Function.identity());

    /// The module `emitter` hands its particles.
    ///
    /// @throws IllegalStateException when the module can't be built
    ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter);

    /// What two entries share when they are the same module. An emitter holds one of each.
    default Object moduleType() {
        return mapCodec();
    }

    MapCodec<? extends ParticleUpdaterBlueprint> mapCodec();

    /// A [ParticleUpdateModule] class with a public no-argument constructor.
    record ByClass(ScriptType type) implements ParticleUpdaterBlueprint {
        public static final MapCodec<ByClass> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("class").forGetter(ByClass::type)
                ).apply(instance, ByClass::new)
        );

        @Override
        public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
            if (!ParticleUpdateModule.class.isAssignableFrom(type.type())) {
                throw new IllegalStateException(type.type().getSimpleName() + " is no " + ParticleUpdateModule.class.getSimpleName());
            }

            try {
                return (ParticleUpdateModule)type.type().getConstructor().newInstance();
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new IllegalStateException("Failed at building particle updater " + type.type().getSimpleName(), e);
            }
        }

        @Override
        public Object moduleType() {
            return type;
        }

        @Override
        public MapCodec<ByClass> mapCodec() {
            return MAP_CODEC;
        }
    }

    /// A [ParticleUpdaterScriptBlueprint] data pack entry.
    record ByData(ResourceKey<ParticleUpdaterScriptBlueprint> module) implements ParticleUpdaterBlueprint {
        public static final MapCodec<ByData> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    ResourceKey.codec(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY).fieldOf("module").forGetter(ByData::module)
                ).apply(instance, ByData::new)
        );

        @Override
        public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
            return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY)
                .getOptional(module)
                .orElseThrow(() -> new IllegalStateException("No particle updater " + module.identifier()))
                .instantiate(emitter);
        }

        @Override
        public Object moduleType() {
            return module;
        }

        @Override
        public MapCodec<ByData> mapCodec() {
            return MAP_CODEC;
        }
    }
}
