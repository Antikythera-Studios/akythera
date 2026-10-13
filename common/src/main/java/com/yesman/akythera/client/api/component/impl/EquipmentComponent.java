package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.EquipmentSlotAnchor;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

/// Draws the item in one equipment slot wherever its host places it — under a [TransformSocket], in that
/// bone's hand. The item patch the holder holds in that slot hangs under it.
///
/// Instantiated per host by [EquipmentComponentBlueprint].
public final class EquipmentComponent implements RenderableComponent<LivingEntityRenderState>, EquipmentSlotAnchor {
    /// The slot whose item is drawn.
    private final EquipmentSlot equipmentSlot;

    /// The context the item model is resolved and posed in.
    private final ItemDisplayContext itemDisplayContext;

    /// The host this component is attached under.
    private final @Nullable ComponentAttachable host;

    /// Where the item sits in [#host]'s space.
    private Transform localTransform;

    /// The elements of [#host]'s space the item takes on.
    private final TransformInheritPolicy transformInheritPolicy;

    /// Whether this component takes part, see [PatchComponent#isEnabled].
    private boolean enabled = true;

    public EquipmentComponent(
        final EquipmentSlot equipmentSlot,
        final ItemDisplayContext itemDisplayContext,
        final @Nullable ComponentAttachable host,
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy
    ) {
        this.equipmentSlot = equipmentSlot;
        this.itemDisplayContext = itemDisplayContext;
        this.host = host;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    public ItemDisplayContext itemDisplayContext() {
        return itemDisplayContext;
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

    /// Resolves the slot's item into the render state's own [ItemStackRenderState] for this
    /// slot and context.
    @Override
    public void extract(final EntityPatch<?> patch, final LivingEntityRenderState state, final float partialTick) {
        if (!(patch.getOriginal() instanceof LivingEntity livingEntity)) return;

        Minecraft.getInstance().getItemModelResolver().updateForLiving(
            ((LivingEntityRenderStateAccessor) state).akythera$itemState(equipmentSlot, itemDisplayContext),
            livingEntity.getItemBySlot(equipmentSlot),
            itemDisplayContext,
            livingEntity
        );
    }

    @Override
    public void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state,
        final float yRot,
        final float xRot
    ) {
        ItemStackRenderState itemState =
            ((LivingEntityRenderStateAccessor) state).akythera$itemState(equipmentSlot, itemDisplayContext);

        if (itemState.isEmpty()) return;

        itemState.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
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
}
