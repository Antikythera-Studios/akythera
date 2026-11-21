package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.AbilityResourceBlueprint;
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
public abstract class AbilityResourceBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public AbilityResourceBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.ABILITY_RESOURCE_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<AbilityResourceBlueprint>, AbilityResourceBlueprint> entries = new HashMap<>();

        buildEntries((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate ability resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, AbilityResourceBlueprint.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildEntries(BiConsumer<ResourceKey<AbilityResourceBlueprint>, AbilityResourceBlueprint> writer);

    @Override
    public String getName() {
        return "Ability Resources";
    }
}
