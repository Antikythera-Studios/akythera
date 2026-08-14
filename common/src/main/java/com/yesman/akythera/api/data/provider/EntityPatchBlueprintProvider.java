package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
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
public abstract class EntityPatchBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public EntityPatchBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<EntityPatchBlueprint>, EntityPatchBlueprint> scripts = new HashMap<>();

        buildScripts((resourceKey, script) -> {
            if (scripts.putIfAbsent(resourceKey, script) != null) {
                throw new IllegalStateException("Duplicate animation script resource key: " + resourceKey);
            }

            scripts.put(resourceKey, script);
        });

        return DataProvider.saveAll(cachedOutput, EntityPatchBlueprint.CODEC, pathProvider::json, scripts);
    }

    protected abstract void buildScripts(BiConsumer<ResourceKey<EntityPatchBlueprint>, EntityPatchBlueprint> writer);

    @Override
    public String getName() {
        return "Entity Patch Scripts";
    }
}
