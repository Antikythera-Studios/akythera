package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/// Writes particle systems as data pack entries of [AkytheraRegistries.DataPack#PARTICLE_SYSTEM_KEY] at datagen.
@NullMarked
public abstract class ParticleSystemBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public ParticleSystemBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<ParticleSystemBlueprint>, ParticleSystemBlueprint> systems = new HashMap<>();

        buildSystems((resourceKey, system) -> {
            if (systems.putIfAbsent(resourceKey, system) != null) {
                throw new IllegalStateException("Duplicate particle system resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, ParticleSystemBlueprint.CODEC, pathProvider::json, systems);
    }

    protected abstract void buildSystems(BiConsumer<ResourceKey<ParticleSystemBlueprint>, ParticleSystemBlueprint> writer);

    @Override
    public String getName() {
        return "Particle Systems";
    }
}
