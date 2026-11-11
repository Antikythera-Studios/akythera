package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
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
public abstract class WidgetBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public WidgetBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.WIDGET_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<WidgetBlueprint>, WidgetBlueprint> entries = new HashMap<>();

        buildWidgets((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate widget blueprint resource key: " + resourceKey);
            }

            entries.put(resourceKey, entry);
        });

        return DataProvider.saveAll(cachedOutput, WidgetBlueprint.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildWidgets(BiConsumer<ResourceKey<WidgetBlueprint>, WidgetBlueprint> writer);

    @Override
    public String getName() {
        return "Widget Blueprints";
    }
}
