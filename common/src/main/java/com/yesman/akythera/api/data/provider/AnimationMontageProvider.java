package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

@NullMarked
public abstract class AnimationMontageProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public AnimationMontageProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<AnimationMontage>, AnimationMontage> montages = new HashMap<>();

        buildAnimMontages((resourceKey, montage) -> {
            if (montages.putIfAbsent(resourceKey, montage) != null) {
                throw new IllegalStateException("Duplicate montage resource key: " + resourceKey);
            }

            montages.put(resourceKey, montage);
        });

        return DataProvider.saveAll(cachedOutput, AnimationMontage.CODEC, pathProvider::json, montages);
    }

    /// Implement this method to define your mod's animation montages
    ///
    /// Use [AnimationMontage.Builder] for more convenient montage creations
    protected abstract void buildAnimMontages(BiConsumer<ResourceKey<AnimationMontage>, AnimationMontage> writer);

    @Override
    public String getName() {
        return "Animation Montages";
    }
}
