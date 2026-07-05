package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.NonNull;

/// A simple [AnimationNotify] implementation that plays a sound in a client
public final class PlaySoundNotify extends AnimationNotify<EntityPatch<?>> {
    public static final MapCodec<PlaySoundNotify> CODEC =
        RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                SoundEvent.CODEC.fieldOf("sound").forGetter(c -> c.soundEvent)
            ).apply(inst, PlaySoundNotify::new)
        );

    private final Holder<@NonNull SoundEvent> soundEvent;

    /// Local sounds are always played in client since notify itself already
    /// replicated from server to clients. No need to send sound playing packet twice.
    public PlaySoundNotify(Holder<@NonNull SoundEvent> soundEvent) {
        super(LogicalSide.CLIENT);

        this.soundEvent = soundEvent;
    }

    @Override
    public void onNotified(EntityPatch<?> entityPatch, PlayableAnimation owner, float prevElapsedTime, float currentElapsedTime) {
        entityPatch.getOwner().playSound(soundEvent.value());
    }

    public Holder<@NonNull SoundEvent> soundEvent() {
        return soundEvent;
    }

    @Override
    public MapCodec<? extends AnimationNotify<?>> mapCodec() {
        return CODEC;
    }
}
