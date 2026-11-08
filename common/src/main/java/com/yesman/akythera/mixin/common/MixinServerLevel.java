package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.PerceptionSettings;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {
    /// Lets the brains within range hear a sound played at a position. The `except` player, left out of the packet
    /// only, is heard too.
    @Inject(
        method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
        at = @At("HEAD")
    )
    private void akythera$hearSound(
        final @Nullable Entity except, final double x, final double y, final double z, final Holder<SoundEvent> sound,
        final SoundSource source, final float volume, final float pitch, final long seed, final CallbackInfo callbackInfo
    ) {
        Vec3 location = new Vec3(x, y, z);

        BrainComponent.forEachTreeNear(
            (ServerLevel)(Object)this, location, sound.value().getRange(volume) + PerceptionSettings.Hearing.MAX_RADIUS,
            tree -> tree.hear(location, sound.value(), volume)
        );
    }

    /// Lets the brains within range hear a sound played by an entity.
    @Inject(
        method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
        at = @At("HEAD")
    )
    private void akythera$hearEntitySound(
        final @Nullable Entity except, final Entity sourceEntity, final Holder<SoundEvent> sound,
        final SoundSource source, final float volume, final float pitch, final long seed, final CallbackInfo callbackInfo
    ) {
        Vec3 location = sourceEntity.position();

        BrainComponent.forEachTreeNear(
            (ServerLevel)(Object)this, location, sound.value().getRange(volume) + PerceptionSettings.Hearing.MAX_RADIUS,
            tree -> tree.hear(location, sound.value(), volume)
        );
    }

    /// Lets the brains within the notification radius feel a game event.
    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V", at = @At("HEAD"))
    private void akythera$feelVibration(final Holder<GameEvent> gameEvent, final Vec3 position, final GameEvent.Context context, final CallbackInfo callbackInfo) {
        BrainComponent.forEachTreeNear(
            (ServerLevel)(Object)this, position, gameEvent.value().notificationRadius(),
            tree -> tree.feelVibration(position, gameEvent.value(), context.sourceEntity())
        );
    }
}
