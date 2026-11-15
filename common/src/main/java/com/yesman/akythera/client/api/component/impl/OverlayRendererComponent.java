package com.yesman.akythera.client.api.component.impl;

import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.client.api.particle.AkytheraOverlayParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.ClientOnly;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The live [AkytheraOverlayParticle]s drawn over its host's model, in the order they spawned. While it is switched
/// off, none is drawn and none spawns.
@ClientOnly @ScriptType
public final class OverlayRendererComponent implements UniqueComponent {
    private final List<AkytheraOverlayParticle> overlays = new ArrayList<>();

    private boolean enabled = true;

    public void add(final AkytheraOverlayParticle overlay) {
        overlays.add(overlay);
    }

    public void remove(final AkytheraOverlayParticle overlay) {
        overlays.remove(overlay);
    }

    /// Read only.
    public List<AkytheraOverlayParticle> getOverlays() {
        return Collections.unmodifiableList(overlays);
    }

    @ScriptFunction(pure = true, label = "Get Overlay", category = "Particle/Overlay", desc = """
        The first live overlay particle a play of the particle system spawned over this entity. Null when there is none
    """)
    public @Nullable AkytheraParticle getOverlay(final ParticleSystemBlueprint system) {
        for (AkytheraOverlayParticle overlay : overlays) {
            if (overlay.getEmitter().getSystem().getBlueprint() == system) return overlay;
        }

        return null;
    }

    @ScriptFunction(label = "Remove Overlays", category = "Particle/Overlay", desc = "Removes the overlay particles the plays of the particle system spawned over this entity")
    public void removeOverlays(final ParticleSystemBlueprint system) {
        for (AkytheraOverlayParticle overlay : List.copyOf(overlays)) {
            if (overlay.getEmitter().getSystem().getBlueprint() == system) overlay.remove();
        }
    }

    @ScriptFunction(label = "Clear Overlays", category = "Particle/Overlay", desc = "Removes every overlay particle drawn over this entity")
    public void clearOverlays() {
        List.copyOf(overlays).forEach(AkytheraOverlayParticle::remove);
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
