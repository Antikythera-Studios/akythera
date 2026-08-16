package com.yesman.akythera.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.api.component.impl.EntityModelComponent;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Inject(
        at = @At(
            value = "HEAD"
        ),
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V"
    )
    private void extractRenderState(final T entity, final S state, final float partialTick, CallbackInfo callbackInfo) {
        LivingEntityRenderStateAccessor stateAccessor = (LivingEntityRenderStateAccessor) state;
        List<PatchModelRenderer.RenderableSubmit> renderables = stateAccessor.akythera$getRenderables();

        renderables.clear();
        stateAccessor.akythera$setModelSubmit(null);

        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            if (entityPatch.getArmatureComponent() != null) {
                stateAccessor.akythera$setPose(entityPatch.getArmatureComponent().getCachedPose());
                stateAccessor.akythera$setArmature(entityPatch.getArmatureComponent().getArmature());
            }

            PatchModelRenderer.collect(entityPatch, null, renderables);
            PatchModelRenderer.extract(renderables, entityPatch, state, partialTick);
        });
    }

    /// Hands the entity's own model draw to the patch's renderables, and submits the rest of them
    /// alongside it. An entity with no renderables — patched or not — keeps the vanilla submit, and so
    /// does one whose renderables include no [EntityModelComponent] to own the model.
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

            if (!renderables.isEmpty()) {
                stateAccessor.akythera$setModelSubmit(
                    new EntityModelComponent.ModelSubmit(model, renderType, overlayCoords, tintedColor, sprite, crumblingOverlay)
                );

                if (akythera$entityModel(livingState) == null) {
                    collector.submitModel(
                        model, renderState, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay
                    );
                }

                PatchModelRenderer.submit(renderables, poseStack, collector, lightCoords, livingState);

                return;
            }
        }

        collector.submitModel(
            model, renderState, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay
        );
    }

    /// Drops the vanilla layers a patched entity never wants: every layer of one drawn as a skeletal
    /// mesh, since they pose the vanilla model's parts that entity never draws, and
    /// [ItemInHandLayer] of any patched entity, whose held items are placed by the sockets instead.
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
        EntityModelComponent entityModel = akythera$entityModel(livingState);

        if (entityModel != null && (layer instanceof ItemInHandLayer || entityModel.resolveSkeletalMesh() != null)) {
            return;
        }

        layer.submit(poseStack, collector, lightCoords, livingState, yRot, xRot);
    }

    @Unique
    private static @Nullable EntityModelComponent akythera$entityModel(final LivingEntityRenderState state) {
        for (PatchModelRenderer.RenderableSubmit submit : ((LivingEntityRenderStateAccessor)state).akythera$getRenderables()) {
            if (submit.component() instanceof EntityModelComponent entityModel) {
                return entityModel;
            }
        }

        return null;
    }
}
