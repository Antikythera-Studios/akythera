package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;

/// Declares an [EntityMovementComponent] on an entity patch.
///
/// @param bodyRotation          how the body yaw of a living owner turns
/// @param canFloatInWater       whether the owner can swim up in water at all
/// @param omnidirectionalSprint whether a player owner may sprint in any direction it moves, not only forward
/// @param jumpRequiresPress     whether each jump from the ground needs the jump key pressed again since the last jump
/// @param surfaceFloat          part of its height a floating owner keeps under the water, 0 for vanilla swimming
/// @param enabled               whether the built [EntityMovementComponent] starts switched on
public record EntityMovementComponentBlueprint(
    EntityMovementComponent.BodyRotation bodyRotation, boolean canFloatInWater, boolean omnidirectionalSprint, boolean jumpRequiresPress,
    float surfaceFloat, boolean enabled
)
    implements ComponentBlueprint<EntityMovementComponent, EntityPatch<?>>
{
    public static final MapCodec<EntityMovementComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EntityMovementComponent.BodyRotation.CODEC.optionalFieldOf("body_rotation", EntityMovementComponent.BodyRotation.VANILLA)
                    .forGetter(EntityMovementComponentBlueprint::bodyRotation),
                Codec.BOOL.optionalFieldOf("can_float_in_water", true)
                    .forGetter(EntityMovementComponentBlueprint::canFloatInWater),
                Codec.BOOL.optionalFieldOf("omnidirectional_sprint", false).forGetter(EntityMovementComponentBlueprint::omnidirectionalSprint),
                Codec.BOOL.optionalFieldOf("jump_requires_press", false).forGetter(EntityMovementComponentBlueprint::jumpRequiresPress),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("surface_float", 0.0F).forGetter(EntityMovementComponentBlueprint::surfaceFloat),
                ENABLED_CODEC.forGetter(EntityMovementComponentBlueprint::enabled)
            )
            .apply(instance, EntityMovementComponentBlueprint::new)
    );

    public EntityMovementComponentBlueprint(final boolean enabled) {
        this(EntityMovementComponent.BodyRotation.VANILLA, true, false, false, 0.0F, enabled);
    }

    @Override
    public EntityMovementComponent instantiate(final EntityPatch<?> blueprintOwner) {
        EntityMovementComponent component = new EntityMovementComponent(
            blueprintOwner, bodyRotation, canFloatInWater, omnidirectionalSprint, jumpRequiresPress, surfaceFloat
        );

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public MapCodec<EntityMovementComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
