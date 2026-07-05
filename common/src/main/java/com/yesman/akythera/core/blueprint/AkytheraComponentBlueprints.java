package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;

public final class AkytheraComponentBlueprints {
    private AkytheraComponentBlueprints() {}

    public static final ModPlatform.Registrar<MapCodec<? extends ComponentBlueprint<?, ?>>> REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends ComponentBlueprint<?, ?>>> ARMATURE =
        REGISTRAR.register("armature", () -> ArmatureComponentBlueprint.CODEC);
}
