package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.behavior.BehaviorTaskBlueprint;
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
public abstract class BehaviorTaskBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public BehaviorTaskBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.BEHAVIOR_TASK_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<BehaviorTaskBlueprint>, BehaviorTaskBlueprint> entries = new HashMap<>();

        buildEntries((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate behavior task resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, BehaviorTaskBlueprint.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildEntries(BiConsumer<ResourceKey<BehaviorTaskBlueprint>, BehaviorTaskBlueprint> writer);

    @Override
    public String getName() {
        return "Behavior Tasks";
    }
}
