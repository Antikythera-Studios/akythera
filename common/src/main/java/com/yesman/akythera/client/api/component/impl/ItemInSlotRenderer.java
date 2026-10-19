package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinderBlueprint;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/// The logical client's [EquipmentSlotBinder], which also draws the slot's item, rigid.
///
/// By default the item is drawn as its item model in [#itemDisplayContext], where its host [TransformSocket] sits, in
/// that bone's hand.
///
/// With [#isModel3d] on, the item is drawn as the 3D model the entity's own renderer wears it with through a layer of
/// its own: a skull or any item on the head in slot `HEAD`, wings in slot `CHEST`. That model sits in the armature's
/// space and rides the bone of the [TransformSocket] hosting this component. An item with no such model draws nothing.
public final class ItemInSlotRenderer extends EquipmentSlotBinder implements RenderableComponent<LivingEntityRenderState> {
    /// The layer of the entity's renderer drawing the slot's 3D model, read on the extract pass. Null while the stack
    /// has none.
    private @Nullable RenderLayer<?, ?> modelLayer;

    public ItemInSlotRenderer(final EquipmentSlotBinderBlueprint blueprint, final ComponentAttachable host) {
        super(blueprint, host);
    }

    @Override
    public Class<LivingEntityRenderState> stateType() {
        return LivingEntityRenderState.class;
    }

    /// Resolves the item into the render state's own [ItemStackRenderState] for this slot and context, or into
    /// [#modelLayer] for a 3D model.
    @Override
    public void extract(final EntityPatch<?> patch, final LivingEntityRenderState state, final float partialTick) {
        if (!(patch.getOriginal() instanceof LivingEntity livingEntity)) return;

        EquipmentSlot equipmentSlot = equipmentSlot();
        ItemStack stack = livingEntity.getItemBySlot(equipmentSlot);

        if (!isModel3d()) {
            Minecraft.getInstance().getItemModelResolver().updateForLiving(
                ((LivingEntityRenderStateAccessor) state).akythera$itemState(equipmentSlot, itemDisplayContext()),
                stack,
                itemDisplayContext(),
                livingEntity
            );

            return;
        }

        modelLayer = null;

        Class<?> layerType = equipmentSlot == EquipmentSlot.HEAD && !HumanoidArmorLayer.shouldRender(stack, equipmentSlot)
            ? CustomHeadLayer.class
            : equipmentSlot == EquipmentSlot.CHEST
                && !SkinnedArmorRenderer.wornLayers(stack, equipmentSlot, EquipmentClientInfo.LayerType.WINGS).isEmpty()
                ? WingsLayer.class
                : null;

        if (stack.isEmpty()
            || layerType == null
            || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(livingEntity) instanceof LivingEntityRenderer<?, ?, ?> renderer)
        ) {
            return;
        }

        for (RenderLayer<?, ?> layer : renderer.layers) {
            if (layerType.isInstance(layer)) modelLayer = layer;
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state,
        final float yRot,
        final float xRot
    ) {
        if (!isModel3d()) {
            ItemStackRenderState itemState =
                ((LivingEntityRenderStateAccessor) state).akythera$itemState(equipmentSlot(), itemDisplayContext());

            if (itemState.isEmpty()) return;

            itemState.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);

            return;
        }

        Pose pose = armature == null ? null : armature.getCachedPose();

        if (modelLayer == null || pose == null) return;

        AssetAccessor<? extends Armature> armatureAsset = armature.getArmature();
        Armature resolvedArmature = armatureAsset.get();
        Bone socketBone = host instanceof TransformSocket socket ? resolvedArmature.getBoneByName(socket.getBoneName()) : null;

        // The layer draws in the vanilla model's space and off its parts, so it is handed the socket
        // bone's skinning matrix with that space under it, and a model in its initial pose. The pose stack
        // arrives in the host's space and is taken back to the armature's
        poseStack.pushPose();

        if (host instanceof TransformComponent space) poseStack.mulPose(space.modelTransform().toMatrix().invert());

        poseStack.mulPose(armature.modelTransform().toMatrix());

        if (socketBone != null) {
            Matrix4f[] boundTransforms = pose.getForwardBoundTransforms(
                armatureAsset, Matrix4fUtils.allocateMatrixArray(resolvedArmature.getBoneCount())
            );

            resolvedArmature.boundBackwardTransform(boundTransforms);
            poseStack.mulPose(boundTransforms[socketBone.getId()]);
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, EntityModel.MODEL_Y_OFFSET, 0.0F);
        modelLayer.getParentModel().resetPose();
        ((RenderLayer) modelLayer).submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
        poseStack.popPose();
    }
}
