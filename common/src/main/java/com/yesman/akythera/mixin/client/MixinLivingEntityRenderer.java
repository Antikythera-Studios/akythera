package com.yesman.akythera.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.collider.ColliderDebugRenderer;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Shadow
    protected abstract void setupRotations(S state, PoseStack poseStack, float bodyRot, float entityScale);

    @Shadow
    protected abstract void scale(S state, PoseStack poseStack);

    @Inject(
        at = @At(
            value = "HEAD"
        ),
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V"
    )
    private void extractRenderState(final T entity, final S state, final float partialTick, CallbackInfo callbackInfo) {
        LivingEntityRenderStateAccessor stateAccessor = (LivingEntityRenderStateAccessor) state;
        List<PatchModelRenderer.RenderableSubmit> renderables = stateAccessor.akythera$getRenderables();
        List<ColliderDebugRenderer.ColliderSubmit> colliders = stateAccessor.akythera$getColliders();

        renderables.clear();
        colliders.clear();
        stateAccessor.akythera$setModelDraw(null);
        stateAccessor.akythera$setVanillaModelHidden(false);
        stateAccessor.akythera$setArmaturePlaced(false);

        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            PatchModelRenderer.collect(entityPatch, null, renderables);
            PatchModelRenderer.extract(renderables, entityPatch, state, partialTick);
            stateAccessor.akythera$setVanillaModelHidden(entityPatch.isBlockingVanillaRender());
            stateAccessor.akythera$setArmaturePlaced(entityPatch.getArmatureComponent() != null);

            if (ColliderDebugRenderer.isEnabled()) {
                ColliderDebugRenderer.collect(entityPatch, colliders);
            }
        });
    }

    /// Turns an armature-placed model by its body yaw alone, as [EntityPatch#getModelTransform] does.
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"
        )
    )
    private void akythera$setupRotations(
        final LivingEntityRenderer<T, S, M> renderer, final S state, final PoseStack poseStack, final float bodyRot, final float entityScale
    ) {
        if (((LivingEntityRenderStateAccessor)state).akythera$isArmaturePlaced()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
        } else {
            setupRotations(state, poseStack, bodyRot, entityScale);
        }
    }

    /// Skips the renderer's own model scale for an armature-placed model.
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"
        )
    )
    private void akythera$scale(final LivingEntityRenderer<T, S, M> renderer, final S state, final PoseStack poseStack) {
        if (!((LivingEntityRenderStateAccessor)state).akythera$isArmaturePlaced()) scale(state, poseStack);
    }

    /// Skips the sleeping bed offset for an armature-placed model.
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0)
    )
    private void akythera$bedOffset(
        final PoseStack poseStack, final float x, final float y, final float z,
        final S state, final PoseStack submitPoseStack, final SubmitNodeCollector collector, final CameraRenderState camera
    ) {
        if (!((LivingEntityRenderStateAccessor)state).akythera$isArmaturePlaced()) poseStack.translate(x, y, z);
    }

    /// Hands the entity's own model draw to the patch's renderables, and submits the rest of them
    /// alongside it. The vanilla submit is skipped while [EntityPatch#isBlockingVanillaRender] holds.
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
        )
    )
    private void akythera$submitRenderableComponents(
        final SubmitNodeCollector collector,
        final Model<Object> model,
        final Object renderState,
        final PoseStack poseStack,
        final RenderType renderType,
        final int lightCoords,
        final int overlayCoords,
        final int tintedColor,
        final @Nullable TextureAtlasSprite sprite,
        final int outlineColor,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        if (renderState instanceof LivingEntityRenderState livingState) {
            LivingEntityRenderStateAccessor stateAccessor = (LivingEntityRenderStateAccessor) livingState;
            List<PatchModelRenderer.RenderableSubmit> renderables = stateAccessor.akythera$getRenderables();
            boolean vanillaModelHidden = stateAccessor.akythera$isVanillaModelHidden();

            if (!renderables.isEmpty() || vanillaModelHidden) {
                stateAccessor.akythera$setModelDraw(
                    new PatchModelRenderer.ModelDraw(renderType, overlayCoords, tintedColor)
                );

                if (!vanillaModelHidden) {
                    collector.submitModel(
                        model, renderState, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay
                    );
                }

                PatchModelRenderer.submit(renderables, poseStack, collector, lightCoords, livingState);
                ColliderDebugRenderer.submit(stateAccessor.akythera$getColliders(), poseStack, collector);

                return;
            }
        }

        collector.submitModel(
            model, renderState, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay
        );
    }

    /// Drops the vanilla layers a patched entity never wants: every layer of one whose vanilla model is
    /// hidden ([EntityPatch#isBlockingVanillaRender]), since they pose model parts that entity never
    /// draws, and [ItemInHandLayer] of any patched entity, whose held items are placed by the sockets
    /// instead.
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"
        )
    )
    @SuppressWarnings("unchecked")
    private void akythera$submitVanillaLayer(
        final RenderLayer<S, M> layer,
        final PoseStack poseStack,
        final SubmitNodeCollector collector,
        final int lightCoords,
        final EntityRenderState state,
        final float yRot,
        final float xRot
    ) {
        // RenderLayer#submit declares its state as EntityRenderState, so the handler has to take
        // that erasure; the layer of a LivingEntityRenderer only ever receives its own state.
        S livingState = (S) state;
        LivingEntityRenderStateAccessor stateAccessor = (LivingEntityRenderStateAccessor) livingState;

        if (stateAccessor.akythera$isVanillaModelHidden()
            || (!stateAccessor.akythera$getRenderables().isEmpty() && layer instanceof ItemInHandLayer)) {
            return;
        }

        layer.submit(poseStack, collector, lightCoords, livingState, yRot, xRot);
    }

}
