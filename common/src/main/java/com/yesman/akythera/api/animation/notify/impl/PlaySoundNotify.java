package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.NonNull;

/// A simple [AnimationNotify] implementation that plays a sound at an entity in a client
public final class PlaySoundNotify extends AnimationNotify<ComponentAttachable> {
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
        super(LogicalSide.CLIENT, NotifyTriggerThread.WORLD);

        this.soundEvent = soundEvent;
    }

    @Override
    public void onNotified(ComponentAttachable owner, PlayableAnimation animation, float prevElapsedTime, float currentElapsedTime) {
        if (owner instanceof EntityPatch<?> entityPatch) {
            entityPatch.getOriginal().playSound(soundEvent.value());
        }
    }

    public Holder<@NonNull SoundEvent> soundEvent() {
        return soundEvent;
    }

    @Override
    public MapCodec<? extends AnimationNotify<?>> mapCodec() {
        return CODEC;
    }
}
