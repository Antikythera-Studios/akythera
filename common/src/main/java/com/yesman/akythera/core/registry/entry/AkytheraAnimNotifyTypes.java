package com.yesman.akythera.core.registry.entry;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.impl.LogNotify;
import com.yesman.akythera.api.animation.notify.impl.PlaySoundNotify;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AkytheraAnimNotifyTypes {
    public static final ModPlatform.Registrar<MapCodec<? extends AnimationNotify<?>>> REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.ANIM_NOTIFY_TYPE_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends AnimationNotify<?>>> LOG = REGISTRAR.register("log", () -> LogNotify.CODEC);
    public static final Holder<MapCodec<? extends AnimationNotify<?>>> PLAY_SOUND = REGISTRAR.register("play_sound", () -> PlaySoundNotify.CODEC);
}
