package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;

/// Builds one [ItemInSlotRenderer] per host, so each host owns its own switches. Attachable under a
/// [TransformSocket], whose bone places the item.
///
/// @param itemDisplayContext     the context the item model is resolved and posed in
/// @param model3d                whether the item is drawn as the 3D model it is worn with, see [ItemInSlotRenderer]
/// @param transform              where the item sits in its host's space, or in its armature's for a 3D model
/// @param transformInheritPolicy the elements of that space the item takes on
public record ItemInSlotRendererBlueprint(
    EquipmentSlot equipmentSlot,
    ItemDisplayContext itemDisplayContext,
    boolean model3d,
    Transform transform,
    TransformInheritPolicy transformInheritPolicy,
    boolean enabled
)
    implements ComponentBlueprint<ItemInSlotRenderer, ComponentAttachable>
{
    public static final MapCodec<ItemInSlotRendererBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EquipmentSlot.CODEC.fieldOf("slot").forGetter(ItemInSlotRendererBlueprint::equipmentSlot),
                ItemDisplayContext.CODEC.fieldOf("display_context").forGetter(ItemInSlotRendererBlueprint::itemDisplayContext),
                Codec.BOOL.optionalFieldOf("model_3d", false).forGetter(ItemInSlotRendererBlueprint::model3d),
                TRANSFORM_CODEC.forGetter(ItemInSlotRendererBlueprint::transform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(ItemInSlotRendererBlueprint::transformInheritPolicy),
                ENABLED_CODEC.forGetter(ItemInSlotRendererBlueprint::enabled)
            )
            .apply(instance, ItemInSlotRendererBlueprint::new)
    );

    @Override
    public ItemInSlotRenderer instantiate(final ComponentAttachable host) {
        ItemInSlotRenderer component = new ItemInSlotRenderer(
            equipmentSlot,
            itemDisplayContext,
            model3d,
            MeshComponentBlueprint.armatureOf(host),
            host,
            transform,
            transformInheritPolicy
        );

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<ItemInSlotRenderer, ComponentAttachable>> mapCodec() {
        return CODEC;
    }
}
