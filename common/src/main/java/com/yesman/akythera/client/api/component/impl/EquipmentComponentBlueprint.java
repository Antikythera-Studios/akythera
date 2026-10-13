package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;

/// Builds one [EquipmentComponent] per host, so each host owns its own switch. Attachable under a
/// [TransformSocket], whose bone places the item.
///
/// @param transform              where the item sits in its host's space
/// @param transformInheritPolicy the elements of that space the item takes on
public record EquipmentComponentBlueprint(
    EquipmentSlot equipmentSlot,
    ItemDisplayContext itemDisplayContext,
    Transform transform,
    TransformInheritPolicy transformInheritPolicy,
    boolean enabled
)
    implements ComponentBlueprint<EquipmentComponent, ComponentAttachable>
{
    public static final MapCodec<EquipmentComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EquipmentSlot.CODEC.fieldOf("slot").forGetter(EquipmentComponentBlueprint::equipmentSlot),
                ItemDisplayContext.CODEC.fieldOf("display_context").forGetter(EquipmentComponentBlueprint::itemDisplayContext),
                TRANSFORM_CODEC.forGetter(EquipmentComponentBlueprint::transform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(EquipmentComponentBlueprint::transformInheritPolicy),
                ENABLED_CODEC.forGetter(EquipmentComponentBlueprint::enabled)
            )
            .apply(instance, EquipmentComponentBlueprint::new)
    );

    @Override
    public EquipmentComponent instantiate(final ComponentAttachable host) {
        EquipmentComponent component = new EquipmentComponent(equipmentSlot, itemDisplayContext, host, transform, transformInheritPolicy);

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<EquipmentComponent, ComponentAttachable>> mapCodec() {
        return CODEC;
    }
}
