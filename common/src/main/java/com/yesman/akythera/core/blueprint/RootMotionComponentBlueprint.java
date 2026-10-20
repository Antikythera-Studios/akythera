package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;

/// Declares a [RootMotionComponent] under an [ArmatureComponent], one per armature.
///
/// @param enabled whether the built [RootMotionComponent] starts switched on
public record RootMotionComponentBlueprint(boolean enabled)
    implements ComponentBlueprint<RootMotionComponent, ArmatureComponent>
{
    public static final MapCodec<RootMotionComponentBlueprint> CODEC =
        ENABLED_CODEC.xmap(RootMotionComponentBlueprint::new, RootMotionComponentBlueprint::enabled);

    @Override
    public RootMotionComponent instantiate(final ArmatureComponent blueprintOwner) {
        RootMotionComponent component = new RootMotionComponent(blueprintOwner);

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public MapCodec<RootMotionComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
