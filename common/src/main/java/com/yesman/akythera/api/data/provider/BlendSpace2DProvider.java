package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.animation.playable.BlendSpace2D;
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

@NullMarked
public abstract class BlendSpace2DProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public BlendSpace2DProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<BlendSpace2D>, BlendSpace2D> blendSpace2Ds = new HashMap<>();

        buildBlendSpace2Ds((resourceKey, blendspace2D) -> {
            if (blendSpace2Ds.putIfAbsent(resourceKey, blendspace2D) != null) {
                throw new IllegalStateException("Duplicate blend space 2d resource key: " + resourceKey);
            }

            blendSpace2Ds.put(resourceKey, blendspace2D);
        });

        return DataProvider.saveAll(cachedOutput, BlendSpace2D.CODEC, pathProvider::json, blendSpace2Ds);
    }

    /// Implement this method to define your mod's blend spaces
    protected abstract void buildBlendSpace2Ds(BiConsumer<ResourceKey<BlendSpace2D>, BlendSpace2D> writer);

    @Override
    public String getName() {
        return "Blend Space 2D";
    }
}
