package com.yesman.akythera.api.data.provider;

import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetScriptBlueprint;
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
public abstract class MotionWarpTargetScriptBlueprintProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;

    public MotionWarpTargetScriptBlueprintProvider(PackOutput pOutput) {
        pathProvider = pOutput.createRegistryElementsPathProvider(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<ResourceKey<MotionWarpTargetScriptBlueprint>, MotionWarpTargetScriptBlueprint> entries = new HashMap<>();

        buildEntries((resourceKey, entry) -> {
            if (entries.putIfAbsent(resourceKey, entry) != null) {
                throw new IllegalStateException("Duplicate motion warp target resource key: " + resourceKey);
            }
        });

        return DataProvider.saveAll(cachedOutput, MotionWarpTargetScriptBlueprint.CODEC, pathProvider::json, entries);
    }

    protected abstract void buildEntries(BiConsumer<ResourceKey<MotionWarpTargetScriptBlueprint>, MotionWarpTargetScriptBlueprint> writer);

    @Override
    public String getName() {
        return "Motion Warp Targets";
    }
}
