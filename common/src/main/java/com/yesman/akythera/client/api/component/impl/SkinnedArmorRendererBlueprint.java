package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.world.entity.EquipmentSlot;

/// Builds one [SkinnedArmorRenderer] per host, so each host owns its own switches. Attachable anywhere under
/// an [ArmatureComponent].
///
/// @param transform              where the piece sits in its armature's space
/// @param transformInheritPolicy the elements of that space the piece takes on
public record SkinnedArmorRendererBlueprint(
    EquipmentSlot equipmentSlot,
    Transform transform,
    TransformInheritPolicy transformInheritPolicy,
    boolean enabled
)
    implements ComponentBlueprint<SkinnedArmorRenderer, ComponentAttachable>
{
    public static final MapCodec<SkinnedArmorRendererBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EquipmentSlot.CODEC.fieldOf("slot").forGetter(SkinnedArmorRendererBlueprint::equipmentSlot),
                TRANSFORM_CODEC.forGetter(SkinnedArmorRendererBlueprint::transform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(SkinnedArmorRendererBlueprint::transformInheritPolicy),
                ENABLED_CODEC.forGetter(SkinnedArmorRendererBlueprint::enabled)
            )
            .apply(instance, SkinnedArmorRendererBlueprint::new)
    );

    @Override
    public SkinnedArmorRenderer instantiate(final ComponentAttachable host) {
        SkinnedArmorRenderer component = new SkinnedArmorRenderer(
            equipmentSlot,
            ArmatureComponent.armatureOf(host),
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
    public MapCodec<? extends ComponentBlueprint<SkinnedArmorRenderer, ComponentAttachable>> mapCodec() {
        return CODEC;
    }
}
