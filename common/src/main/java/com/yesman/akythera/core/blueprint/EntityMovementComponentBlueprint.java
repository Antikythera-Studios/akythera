package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityMovementComponent;

/// Declares an [EntityMovementComponent] on an entity patch.
///
/// @param enabled whether the built [EntityMovementComponent] starts switched on
public record EntityMovementComponentBlueprint(boolean enabled)
    implements ComponentBlueprint<EntityMovementComponent, EntityPatch<?>>
{
    public static final MapCodec<EntityMovementComponentBlueprint> CODEC =
        ENABLED_CODEC.xmap(EntityMovementComponentBlueprint::new, EntityMovementComponentBlueprint::enabled);

    @Override
    public EntityMovementComponent instantiate(final EntityPatch<?> blueprintOwner) {
        EntityMovementComponent component = new EntityMovementComponent(blueprintOwner);

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public MapCodec<EntityMovementComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
