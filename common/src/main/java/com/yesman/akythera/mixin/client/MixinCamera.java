package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityPossessionEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.camera.CameraComponent;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    private @Nullable Entity entity;

    @Shadow
    private @Nullable Level level;

    @Shadow
    private boolean detached;

    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Shadow @Final
    private Quaternionf rotation;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /// Aims the camera by the [CameraComponent]'s rotation while it holds the view.
    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"),
        method = "alignWithEntity(F)V"
    )
    private float akythera$cameraYRot(final Entity viewed, final float partialTicks) {
        CameraComponent camera = CameraComponent.holdingView();

        return camera != null && camera.getOwner().getOriginal() == viewed ? camera.getViewYRot(partialTicks) : viewed.getViewYRot(partialTicks);
    }

    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"),
        method = "alignWithEntity(F)V"
    )
    private float akythera$cameraXRot(final Entity viewed, final float partialTicks) {
        CameraComponent camera = CameraComponent.holdingView();

        return camera != null && camera.getOwner().getOriginal() == viewed ? camera.getViewXRot(partialTicks) : viewed.getViewXRot(partialTicks);
    }

    /// Places the camera at the [CameraComponent]'s location in place of vanilla's distance behind the eyes, then turns
    /// it by the component's rotation. Third person only.
    @Inject(at = @At(value = "TAIL"), method = "alignWithEntity(F)V")
    private void akythera$placeCamera(final float partialTicks, final CallbackInfo callbackInfo) {
        CameraComponent camera = CameraComponent.holdingView();

        if (camera == null || !detached || entity == null || level == null || camera.getOwner().getOriginal() != entity) return;

        if (camera.hasCustomLocation()) {
            float eye = Mth.lerp(partialTicks, eyeHeightOld, eyeHeight);
            Vec3 pivot = new Vec3(
                Mth.lerp(partialTicks, entity.xo, entity.getX()),
                Mth.lerp(partialTicks, entity.yo, entity.getY()) + eye,
                Mth.lerp(partialTicks, entity.zo, entity.getZ())
            );

            setPosition(camera.locate(level, pivot, eye, rotation));
        }

        Vec3 look = camera.lookDirection(rotation);

        if (look != null) setRotation((float)VectorUtils.getVectorYRot(look), (float)VectorUtils.getVectorXRot(look));
    }

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
