package com.yesman.akythera.core.blueprint.warping;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.warping.DataDrivenMotionWarpTarget;
import com.yesman.akythera.api.animation.warping.MotionWarpTarget;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/// The data pack entry for a [MotionWarpTarget]: the [BlueprintBody] a [DataDrivenMotionWarpTarget] runs.
public record MotionWarpTargetScriptBlueprint(BlueprintBody body) implements Blueprint<MotionWarpTarget, EntityPatch<?>> {
    public static final MapCodec<MotionWarpTargetScriptBlueprint> MAP_CODEC =
        BlueprintBody.MAP_CODEC.xmap(MotionWarpTargetScriptBlueprint::new, MotionWarpTargetScriptBlueprint::body);

    public static final Codec<MotionWarpTargetScriptBlueprint> CODEC = MAP_CODEC.codec();

    @Override
    @SuppressWarnings("unchecked")
    public MotionWarpTarget instantiate(final EntityPatch<?> owner) {
        Registry<MotionWarpTargetScriptBlueprint> registry =
            StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY);

        return new DataDrivenMotionWarpTarget((Holder.Reference<MotionWarpTargetScriptBlueprint>)registry.wrapAsHolder(this), body, owner);
    }

    @Override
    public MapCodec<MotionWarpTargetScriptBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
