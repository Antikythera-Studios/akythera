package com.yesman.akythera.core.registry.entry;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.impl.LogNotifyState;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AkytheraAnimNotifyStateTypes {
    public static final ModPlatform.Registrar<MapCodec<? extends AnimationNotifyState<?>>> REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.ANIM_NOTIFY_STATE_TYPE_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends AnimationNotifyState<?>>> LOG = REGISTRAR.register("log", () -> LogNotifyState.CODEC);
}
