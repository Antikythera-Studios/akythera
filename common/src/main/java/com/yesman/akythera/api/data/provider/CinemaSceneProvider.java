package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.cinema.CinemaScene;
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

/// Writes cinema scenes as data pack entries of [AkytheraRegistries.DataPack#CINEMA_SCENE_KEY] at datagen.
@NullMarked
public abstract class CinemaSceneProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public CinemaSceneProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.CINEMA_SCENE_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<CinemaScene>, CinemaScene> scenes = new HashMap<>();

        buildScenes((resourceKey, scene) -> {
            if (scenes.putIfAbsent(resourceKey, scene) != null) {
                throw new IllegalStateException("Duplicate cinema scene resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, CinemaScene.CODEC, pathProvider::json, scenes);
    }

    protected abstract void buildScenes(BiConsumer<ResourceKey<CinemaScene>, CinemaScene> writer);

    @Override
    public String getName() {
        return "Cinema Scenes";
    }
}
