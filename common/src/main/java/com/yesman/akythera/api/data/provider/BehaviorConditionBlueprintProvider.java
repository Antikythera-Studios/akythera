package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.behavior.BehaviorConditionBlueprint;
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
public abstract class BehaviorConditionBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public BehaviorConditionBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.BEHAVIOR_CONDITION_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<BehaviorConditionBlueprint>, BehaviorConditionBlueprint> entries = new HashMap<>();

        buildEntries((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate behavior condition resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, BehaviorConditionBlueprint.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildEntries(BiConsumer<ResourceKey<BehaviorConditionBlueprint>, BehaviorConditionBlueprint> writer);

    @Override
    public String getName() {
        return "Behavior Conditions";
    }
}
