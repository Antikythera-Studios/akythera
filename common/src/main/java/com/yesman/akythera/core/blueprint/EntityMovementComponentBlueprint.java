package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;

/// Declares an [EntityMovementComponent] on an entity patch.
///
/// @param bodyRotation how the body yaw of a living owner turns
/// @param enabled      whether the built [EntityMovementComponent] starts switched on
public record EntityMovementComponentBlueprint(EntityMovementComponent.BodyRotation bodyRotation, boolean enabled)
    implements ComponentBlueprint<EntityMovementComponent, EntityPatch<?>>
{
    public static final MapCodec<EntityMovementComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EntityMovementComponent.BodyRotation.CODEC.optionalFieldOf("body_rotation", EntityMovementComponent.BodyRotation.VANILLA)
                    .forGetter(EntityMovementComponentBlueprint::bodyRotation),
                ENABLED_CODEC.forGetter(EntityMovementComponentBlueprint::enabled)
            )
            .apply(instance, EntityMovementComponentBlueprint::new)
    );

    public EntityMovementComponentBlueprint(final boolean enabled) {
        this(EntityMovementComponent.BodyRotation.VANILLA, enabled);
    }

    @Override
    public EntityMovementComponent instantiate(final EntityPatch<?> blueprintOwner) {
        EntityMovementComponent component = new EntityMovementComponent(blueprintOwner, bodyRotation);

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public MapCodec<EntityMovementComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
