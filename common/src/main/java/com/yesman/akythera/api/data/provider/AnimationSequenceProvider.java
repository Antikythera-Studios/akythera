package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.animation.playable.AnimationSequence;
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
public abstract class AnimationSequenceProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public AnimationSequenceProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<AnimationSequence>, AnimationSequence> animSequences = new HashMap<>();

        buildAnimSequences((resourceKey, animSequence) -> {
            if (animSequences.putIfAbsent(resourceKey, animSequence) != null) {
                throw new IllegalStateException("Duplicate anim sequence resource key: " + resourceKey);
            }

            animSequences.put(resourceKey, animSequence);
        });

        return DataProvider.saveAll(cachedOutput, AnimationSequence.CODEC, pathProvider::json, animSequences);
    }

    /// Implement this method to define your mod's animation sequences
    protected abstract void buildAnimSequences(BiConsumer<ResourceKey<AnimationSequence>, AnimationSequence> writer);

    @Override
    public String getName() {
        return "Animation Sequences";
    }
}
