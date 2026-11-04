package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityPossessionEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    private @Nullable Entity entity;

    /// The patch [BuiltInEventHooks.Entity#POSSESSED] was last posted for.
    @Unique
    private @Nullable EntityPatch<?> akythera$possessed;

    @Inject(at = @At(value = "TAIL"), method = "setEntity(Lnet/minecraft/world/entity/Entity;)V")
    private void akythera$onSetEntity(final Entity newEntity, final CallbackInfo callbackInfo) {
        akythera$updatePossession();
    }

    @Inject(at = @At(value = "TAIL"), method = "reset()V")
    private void akythera$onReset(final CallbackInfo callbackInfo) {
        akythera$updatePossession();
    }

    @Inject(at = @At(value = "HEAD"), method = "tick()V")
    private void akythera$onTick(final CallbackInfo callbackInfo) {
        akythera$updatePossession();
    }

    /// Posts [BuiltInEventHooks.Entity#UNPOSSESSED] for the patch the camera left, then
    /// [BuiltInEventHooks.Entity#POSSESSED] for the one it follows now.
    ///
    /// Compared by patch, not by entity: a world reload and a copy build a new patch for the same entity, which
    /// the camera never sees through [Camera#setEntity]. [Camera#reset] clears the entity without calling it
    /// either. The per-tick check catches a patch swapped in after the camera settled on its entity.
    @Unique
    private void akythera$updatePossession() {
        EntityPatch<?> current = AkytheraPatches.getEntityPatch(entity);

        if (current == akythera$possessed) return;

        EntityPatch<?> left = akythera$possessed;
        akythera$possessed = current;

        if (left != null) {
            BuiltInEventHooks.Entity.UNPOSSESSED.postWithListener(new EntityPossessionEvent.Unpossessed(left), left.getEntityEventListener());
        }

        if (current != null) {
            BuiltInEventHooks.Entity.POSSESSED.postWithListener(new EntityPossessionEvent.Possessed(current), current.getEntityEventListener());
        }
    }
}
