package com.yesman.akythera.api.component.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;

import java.util.Map;

/// Declares a [SpringArmComponent].
///
/// @param transform              the pivot inside the host's space
/// @param transformInheritPolicy the elements of the host's space the pivot takes on
/// @param length                 how far the arm's end is from its pivot, in blocks
/// @param childBlueprints        what the arm builds its children from, keyed by the name they are attached under
/// @param enabled                whether the built [SpringArmComponent] starts switched on
public record SpringArmComponentBlueprint(
    Transform transform,
    TransformInheritPolicy transformInheritPolicy,
    double length,
    Map<String, ComponentBlueprint<?, ?>> childBlueprints,
    boolean enabled
) implements ComponentBlueprint<SpringArmComponent, ComponentAttachable>
{
    public static final MapCodec<SpringArmComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                TRANSFORM_CODEC.forGetter(SpringArmComponentBlueprint::transform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(SpringArmComponentBlueprint::transformInheritPolicy),
                Codec.DOUBLE.optionalFieldOf("length", 0.0D).forGetter(SpringArmComponentBlueprint::length),
                ComponentBlueprint.componentMapCodec(() -> AkytheraRegistries.Frozen.ENTITY_PATCH_COMPONENT_TYPES)
                    .optionalFieldOf("components", Map.of())
                    .forGetter(SpringArmComponentBlueprint::childBlueprints),
                ENABLED_CODEC.forGetter(SpringArmComponentBlueprint::enabled)
            )
            .apply(instance, SpringArmComponentBlueprint::new)
    );

    @Override
    public SpringArmComponent instantiate(final ComponentAttachable blueprintOwner) {
        SpringArmComponent component = new SpringArmComponent(blueprintOwner, transform, transformInheritPolicy, length, childBlueprints);

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public MapCodec<SpringArmComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
