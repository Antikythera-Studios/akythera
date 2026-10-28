package com.yesman.akythera.client.api.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.renderer.TrailRenderer;
import com.yesman.akythera.client.core.renderer.collider.ColliderDebugRenderer;
import com.yesman.akythera.client.core.renderer.collider.ColliderWireframe;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/// The particle group of [AkytheraParticleRenderTypes#TRAIL]: queues every [AkytheraTrailParticle]'s draw on the
/// [TrailRenderer]. Trails are not frustum-culled, since one spans its whole swept path.
///
/// In the F3+B hitbox scene, it also outlines every sampled edge, colored by its lifetime left: red when sampled,
/// through the rainbow to violet when it expires.
public class AkytheraTrailParticleGroup extends ParticleGroup<Particle> {
    /// The hue of an edge with no lifetime left. A fresh edge is 0, red.
    private static final float EXPIRED_HUE = 0.8F;

    public AkytheraTrailParticleGroup(final ParticleEngine engine) {
        super(engine);
    }

    @Override
    public ParticleGroupRenderState extractRenderState(final Frustum frustum, final Camera camera, final float partialTickTime) {
        if (!TrailRenderer.isAvailable()) return new State(List.of(), List.of());

        List<TrailRenderer.Trail> trails = particles.stream()
            .map(particle -> ((AkytheraTrailParticle)particle).extractTrail(camera.position(), partialTickTime, -1, UnaryOperator.identity()))
            .filter(Objects::nonNull)
            .toList();
        List<AkytheraTrailParticle.DebugEdge> debugEdges = ColliderDebugRenderer.isEnabled()
            ? particles.stream().flatMap(particle -> ((AkytheraTrailParticle)particle).debugEdges(camera.position(), partialTickTime).stream()).toList()
            : List.of();

        return new State(trails, debugEdges);
    }

    private record State(List<TrailRenderer.Trail> trails, List<AkytheraTrailParticle.DebugEdge> debugEdges) implements ParticleGroupRenderState {
        @Override
        public void submit(final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
            trails.forEach(TrailRenderer.getInstance()::submit);

            if (debugEdges.isEmpty()) return;

            submitNodeCollector.submitCustomGeometry(new PoseStack(), RenderTypes.lines(), (pose, buffer) -> {
                for (AkytheraTrailParticle.DebugEdge edge : debugEdges) {
                    int color = 0xFF000000 | Mth.hsvToRgb((1.0F - edge.lifeLeft()) * EXPIRED_HUE, 1.0F, 1.0F);

                    ColliderWireframe.line(buffer, pose, edge.start(), edge.end(), color);
                }
            });
        }
    }
}
