package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.client.core.renderer.TrailRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/// The particle group of [AkytheraParticleRenderTypes#TRAIL]: queues every [AkytheraTrailParticle]'s draw on the
/// [TrailRenderer]. Trails are not frustum-culled, since one spans its whole swept path.
public class AkytheraTrailParticleGroup extends ParticleGroup<Particle> {
    public AkytheraTrailParticleGroup(final ParticleEngine engine) {
        super(engine);
    }

    @Override
    public ParticleGroupRenderState extractRenderState(final Frustum frustum, final Camera camera, final float partialTickTime) {
        if (!TrailRenderer.isAvailable()) return new State(List.of());

        return new State(
            particles.stream()
                .map(particle -> ((AkytheraTrailParticle)particle).extractTrail(camera.position(), partialTickTime, -1, UnaryOperator.identity()))
                .filter(Objects::nonNull)
                .toList()
        );
    }

    private record State(List<TrailRenderer.Trail> trails) implements ParticleGroupRenderState {
        @Override
        public void submit(final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
            trails.forEach(TrailRenderer.getInstance()::submit);
        }
    }
}
