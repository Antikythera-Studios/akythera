package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.behavior.BehaviorTreeBlueprint;
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
public abstract class BehaviorTreeBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public BehaviorTreeBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<BehaviorTreeBlueprint>, BehaviorTreeBlueprint> entries = new HashMap<>();

        buildEntries((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate behavior tree resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, BehaviorTreeBlueprint.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildEntries(BiConsumer<ResourceKey<BehaviorTreeBlueprint>, BehaviorTreeBlueprint> writer);

    @Override
    public String getName() {
        return "Behavior Trees";
    }
}
