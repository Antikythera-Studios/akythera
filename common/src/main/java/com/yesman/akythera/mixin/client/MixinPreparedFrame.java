package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Skinned meshes join the frame where the feature renderers do, so the prepared frame drives them
/// wherever `FeatureRenderDispatcher#renderAllFeatures` is called from — the level, the inventory,
/// and the picture-in-picture renderers alike.
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class MixinPreparedFrame {
    @Inject(at = @At("TAIL"), method = "executeSolid()V")
    private void akythera$renderSolidSkinnedMeshes(CallbackInfo callbackInfo) {
        PatchModelRenderer.getInstance().renderSolid();
    }

    @Inject(at = @At("TAIL"), method = "executeTranslucent()V")
    private void akythera$renderTranslucentSkinnedMeshes(CallbackInfo callbackInfo) {
        PatchModelRenderer.getInstance().renderTranslucent();
    }

    @Inject(at = @At("TAIL"), method = "close()V")
    private void akythera$endSkinnedMeshFrame(CallbackInfo callbackInfo) {
        PatchModelRenderer.getInstance().clear();
        PatchModelRenderer.getInstance().endFrame();
    }
}
