package com.yesman.akythera.client.api.particle;

import net.minecraft.client.particle.ParticleRenderType;

import java.util.List;

/// The particle groups Akythera adds to the particle engine, drawn after vanilla's in [#RENDER_ORDER].
public final class AkytheraParticleRenderTypes {
    private AkytheraParticleRenderTypes() {}

    /// [AkytheraMeshParticle]s, drawn by [AkytheraMeshParticleGroup].
    public static final ParticleRenderType MESH = new ParticleRenderType("AKYTHERA_MESH", "AM");

    /// [AkytheraTrailParticle]s, drawn by [AkytheraTrailParticleGroup].
    public static final ParticleRenderType TRAIL = new ParticleRenderType("AKYTHERA_TRAIL", "AT");

    public static final List<ParticleRenderType> RENDER_ORDER = List.of(MESH, TRAIL);
}
