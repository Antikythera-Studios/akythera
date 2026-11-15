package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;

/// Builds one [OverlayRendererComponent] per host.
public record OverlayRendererBlueprint(boolean enabled) implements ComponentBlueprint<OverlayRendererComponent, ComponentAttachable> {
    public static final MapCodec<OverlayRendererBlueprint> CODEC =
        ENABLED_CODEC.xmap(OverlayRendererBlueprint::new, OverlayRendererBlueprint::enabled);

    @Override
    public OverlayRendererComponent instantiate(final ComponentAttachable host) {
        OverlayRendererComponent component = new OverlayRendererComponent();

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient;
    }

    @Override
    public MapCodec<OverlayRendererBlueprint> mapCodec() {
        return CODEC;
    }
}
