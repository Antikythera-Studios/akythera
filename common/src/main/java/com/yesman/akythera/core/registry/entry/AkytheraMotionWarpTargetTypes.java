package com.yesman.akythera.core.registry.entry;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.warping.AttackTargetWarpTarget;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AkytheraMotionWarpTargetTypes {
    public static final ModPlatform.Registrar<MapCodec<? extends MotionWarpTargetBlueprint>> REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.MOTION_WARP_TARGET_TYPE_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends MotionWarpTargetBlueprint>> CLASS = REGISTRAR.register("class", () -> MotionWarpTargetBlueprint.ByClass.MAP_CODEC);

    public static final Holder<MapCodec<? extends MotionWarpTargetBlueprint>> DATA = REGISTRAR.register("data", () -> MotionWarpTargetBlueprint.ByData.MAP_CODEC);

    public static final Holder<MapCodec<? extends MotionWarpTargetBlueprint>> ATTACK_TARGET = REGISTRAR.register("attack_target", () -> AttackTargetWarpTarget.MAP_CODEC);
}
