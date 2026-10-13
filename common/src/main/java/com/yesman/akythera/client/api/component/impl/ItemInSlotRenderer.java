package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.EquipmentSlotAnchor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.math.Transform;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/// Draws the item in one equipment slot, rigid.
///
/// By default the item is drawn as its item model in [#itemDisplayContext], wherever the host places it —
/// under a [TransformSocket], in that bone's hand.
///
/// With [#model3d] on, the item is drawn as the 3D model the entity's own renderer wears it with through a
/// layer of its own: a skull or any item on the head in slot `HEAD`, wings in slot `CHEST`. That model sits
/// in the armature's space and rides the bone of the [TransformSocket] hosting this component. An item with
/// no such model draws nothing.
///
/// The item patch the holder holds in that slot hangs under it.
///
/// Instantiated per host by [ItemInSlotRendererBlueprint].
public final class ItemInSlotRenderer implements RenderableComponent<LivingEntityRenderState>, EquipmentSlotAnchor {
    /// The slot whose item is drawn.
    private final EquipmentSlot equipmentSlot;

    /// The context the item model is resolved and posed in.
    private final ItemDisplayContext itemDisplayContext;

    /// Whether the item is drawn as the 3D model it is worn with, not as its item model.
    private final boolean model3d;

    /// The armature a 3D model sits in, null where the host chain reaches none.
    private final @Nullable ArmatureComponent armature;

    /// The host this component is attached under.
    private final @Nullable ComponentAttachable host;

    /// Where the item sits in [#host]'s space, or in [#armature]'s for a 3D model.
    private Transform localTransform;

    /// The elements of that space the item takes on.
    private final TransformInheritPolicy transformInheritPolicy;

    /// Whether this component takes part, see [PatchComponent#isEnabled].
    private boolean enabled = true;

    /// The layer of the entity's renderer drawing the slot's 3D model, read on the extract pass. Null while
    /// the stack has none.
    private @Nullable RenderLayer<?, ?> modelLayer;

    public ItemInSlotRenderer(
        final EquipmentSlot equipmentSlot,
        final ItemDisplayContext itemDisplayContext,
        final boolean model3d,
        final @Nullable ArmatureComponent armature,
        final @Nullable ComponentAttachable host,
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy
    ) {
        this.equipmentSlot = equipmentSlot;
        this.itemDisplayContext = itemDisplayContext;
        this.model3d = model3d;
        this.armature = armature;
        this.host = host;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    public ItemDisplayContext itemDisplayContext() {
        return itemDisplayContext;
    }

    public boolean isModel3d() {
        return model3d;
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    // ***************************************************************
    // RenderableComponent
    // ***************************************************************

    @Override
    public Class<LivingEntityRenderState> stateType() {
        return LivingEntityRenderState.class;
    }

    /// Resolves the item into the render state's own [ItemStackRenderState] for this slot and context, or
    /// into [#modelLayer] for a 3D model.
    @Override
    public void extract(final EntityPatch<?> patch, final LivingEntityRenderState state, final float partialTick) {
        if (!(patch.getOriginal() instanceof LivingEntity livingEntity)) return;

        ItemStack stack = livingEntity.getItemBySlot(equipmentSlot);

        if (!model3d) {
            Minecraft.getInstance().getItemModelResolver().updateForLiving(
                ((LivingEntityRenderStateAccessor) state).akythera$itemState(equipmentSlot, itemDisplayContext),
                stack,
                itemDisplayContext,
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
        if (!model3d) {
            ItemStackRenderState itemState =
                ((LivingEntityRenderStateAccessor) state).akythera$itemState(equipmentSlot, itemDisplayContext);

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
        // bone's skinning matrix with that space under it, and a model in its initial pose
        poseStack.pushPose();

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

    // ***************************************************************
    // EquipmentSlotAnchor
    // ***************************************************************

    @Override
    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
    }

    @Override
    public Transform localTransform() {
        return localTransform;
    }

    @Override
    public void setLocalTransform(final Transform transform) {
        localTransform = transform;
    }

    @Override
    public @Nullable ComponentAttachable host() {
        return host;
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return transformInheritPolicy;
    }

    /// A 3D model sits in its armature's space.
    @Override
    public Transform parentModelTransform() {
        return armature != null && model3d ? armature.modelTransform() : EquipmentSlotAnchor.super.parentModelTransform();
    }

    @Override
    public Transform previousParentModelTransform() {
        return armature != null && model3d
            ? armature.previousModelTransform()
            : EquipmentSlotAnchor.super.previousParentModelTransform();
    }
}
