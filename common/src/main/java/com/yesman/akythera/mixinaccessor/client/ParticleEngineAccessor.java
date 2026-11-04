package com.yesman.akythera.mixinaccessor.client;

import net.minecraft.client.particle.Particle;

public interface ParticleEngineAccessor {
    /// Adds `particle` to its group at once, so it draws from this frame and ticks from the next tick. While the engine
    /// ticks, or for a particle with a limit, it is queued as [net.minecraft.client.particle.ParticleEngine#add] does.
    void akythera$addNow(final Particle particle);
}
