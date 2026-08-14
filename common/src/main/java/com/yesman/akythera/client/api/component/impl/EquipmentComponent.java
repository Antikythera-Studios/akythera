package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

/// Draws the item in one equipment slot wherever its host places it — under a [BoneSocket], in that
/// bone's hand.
///
/// @param equipmentSlot      the slot whose item is drawn
/// @param itemDisplayContext the context the item model is resolved and posed in
public record EquipmentComponent(EquipmentSlot equipmentSlot, ItemDisplayContext itemDisplayContext)
    implements RenderableComponent<LivingEntityRenderState>, ComponentBlueprint<EquipmentComponent, ComponentAttachable>
{
    public static final MapCodec<EquipmentComponent> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EquipmentSlot.CODEC.fieldOf("slot").forGetter(EquipmentComponent::equipmentSlot),
                ItemDisplayContext.CODEC.fieldOf("display_context").forGetter(EquipmentComponent::itemDisplayContext)
            )
            .apply(instance, EquipmentComponent::new)
    );

    /// ***************************************************************
    /// [RenderableComponent]
    /// ***************************************************************

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

    /// ***************************************************************
    /// [ComponentBlueprint]
    /// ***************************************************************

    @Override
    public EquipmentComponent instantiate(final ComponentAttachable host) {
        return this;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<EquipmentComponent, ComponentAttachable>> mapCodec() {
        return CODEC;
    }
}
