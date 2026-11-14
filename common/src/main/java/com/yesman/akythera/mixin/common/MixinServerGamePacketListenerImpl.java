package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.cinema.CinemaScenePlayback;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.cinema.CinemaScene;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    /// Accepts the move of a [CinemaScene.Actor#ignoreBlockCollision] actor into blocks, which the server would send
    /// back to where it was.
    @Inject(
        at = @At(value = "HEAD"),
        method = "isEntityCollidingWithAnythingNew(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;DDD)Z",
        cancellable = true
    )
    private void akythera$moveIntoBlocksInCinemaScene(
        final LevelReader level, final Entity entity, final AABB box, final double x, final double y, final double z,
        final CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EntityPatch<?> patch = AkytheraPatches.getEntityPatch(entity);
        CinemaScenePlayback playback = patch == null ? null : patch.getCinemaScenePlayback();

        if (playback != null && playback.getActor().ignoreBlockCollision()) callbackInfo.setReturnValue(false);
    }
}
