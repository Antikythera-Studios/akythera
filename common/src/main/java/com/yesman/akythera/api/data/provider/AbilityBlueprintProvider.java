package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.AbilityBlueprint;
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
public abstract class AbilityBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public AbilityBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.ABILITY_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<AbilityBlueprint>, AbilityBlueprint> entries = new HashMap<>();

        buildAbilities((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate ability blueprint resource key: " + resourceKey);
            }

            entries.put(resourceKey, entry);
        });

        return DataProvider.saveAll(cachedOutput, AbilityBlueprint.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildAbilities(BiConsumer<ResourceKey<AbilityBlueprint>, AbilityBlueprint> writer);

    @Override
    public String getName() {
        return "Ability Blueprints";
    }
}
