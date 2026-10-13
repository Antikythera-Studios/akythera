package com.yesman.akythera.mixin.client;

import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {
    /// Skips the renderer's offset (the player's crouch offset) for an armature-placed model, see
    /// [LivingEntityRenderStateAccessor#akythera$isArmaturePlaced].
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getRenderOffset(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 akythera$renderOffset(final EntityRenderer renderer, final EntityRenderState state) {
        return state instanceof LivingEntityRenderStateAccessor accessor && accessor.akythera$isArmaturePlaced()
            ? Vec3.ZERO
            : renderer.getRenderOffset(state);
    }
}
