package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.api.world.ability.AbilityEffect;
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
public abstract class AbilityEffectProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public AbilityEffectProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.ABILITY_EFFECT_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<AbilityEffect>, AbilityEffect> entries = new HashMap<>();

        buildEntries((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate ability effect key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, AbilityEffect.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildEntries(BiConsumer<ResourceKey<AbilityEffect>, AbilityEffect> writer);

    @Override
    public String getName() {
        return "Ability Effects";
    }
}
