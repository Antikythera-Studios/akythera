package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Where an [AkytheraParticleSystem]'s emitters put the particles they spawn, where they look up the particle pieces
/// they name, and what particles of a system with no level collide with.
public interface ParticleHost {
    /// The particle engine of the client level, with pieces from the synced registry.
    ParticleHost LEVEL = new ParticleHost() {
        @Override
        public void add(final AkytheraParticle particle) {
            Minecraft.getInstance().particleEngine.add((Particle)particle);
        }

        @Override
        public @Nullable ParticleBlueprint piece(final ResourceKey<ParticleBlueprint> key) {
            return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY).getValue(key);
        }
    };

    void add(AkytheraParticle particle);

    /// The piece `key` names, or null when there is none.
    @Nullable ParticleBlueprint piece(ResourceKey<ParticleBlueprint> key);

    /// The part of `motion` a particle with physics, whose bounding box is `box`, moves before it hits something. Only
    /// asked for a system with no level. Nothing blocks by default.
    default Vec3 collide(final AABB box, final Vec3 motion) {
        return motion;
    }
}
