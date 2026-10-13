package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
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

/// Writes particle pieces as data pack entries of [AkytheraRegistries.DataPack#PARTICLE_PIECE_KEY] at datagen.
@NullMarked
public abstract class ParticleBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public ParticleBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<ParticleBlueprint>, ParticleBlueprint> pieces = new HashMap<>();

        buildPieces((resourceKey, piece) -> {
            if (pieces.putIfAbsent(resourceKey, piece) != null) {
                throw new IllegalStateException("Duplicate particle piece resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, ParticleBlueprint.CODEC, pathProvider::json, pieces);
    }

    protected abstract void buildPieces(BiConsumer<ResourceKey<ParticleBlueprint>, ParticleBlueprint> writer);

    @Override
    public String getName() {
        return "Particle Pieces";
    }
}
