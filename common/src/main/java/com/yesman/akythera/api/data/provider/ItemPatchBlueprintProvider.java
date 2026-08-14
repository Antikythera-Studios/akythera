package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
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
public abstract class ItemPatchBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public ItemPatchBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.ITEM_PATCH_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<ItemPatchBlueprint>, ItemPatchBlueprint> blueprints = new HashMap<>();

        buildItemPatches((resourceKey, blueprint) -> {
            if (blueprints.putIfAbsent(resourceKey, blueprint) != null) {
                throw new IllegalStateException("Duplicate item patch blueprint resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, ItemPatchBlueprint.CODEC, pathProvider::json, blueprints);
    }

    /// Implement this method to define your mod's item patch blueprints
    protected abstract void buildItemPatches(BiConsumer<ResourceKey<ItemPatchBlueprint>, ItemPatchBlueprint> writer);

    @Override
    public String getName() {
        return "Item Patch Blueprints";
    }
}