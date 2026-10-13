package com.yesman.akythera.client.api.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TickableDynamicComponent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/// The particle group of [AkytheraParticleRenderTypes#MESH]: every [AkytheraMeshParticle] whose position is in view
/// submits its own model, the farthest first, so a translucent model never hides one behind it from the depth test.
///
/// Every particle that is a [ComponentAttachable] frame-updates its components here first, in view or not.
public class AkytheraMeshParticleGroup extends ParticleGroup<Particle> {
    public AkytheraMeshParticleGroup(final ParticleEngine engine) {
        super(engine);
    }

    @Override
    public ParticleGroupRenderState extractRenderState(final Frustum frustum, final Camera camera, final float partialTickTime) {
        // A frame's real seconds: delta ticks times one tick's seconds. Zero while paused
        float frameTime = Minecraft.getInstance().isPaused() ? 0.0F : Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks() * 0.05F;

        frameUpdate(particles, frameTime, partialTickTime);

        Vec3 cameraPosition = camera.position();
        List<AkytheraMeshParticle> visible = particles.stream()
            .map(particle -> (AkytheraMeshParticle)particle)
            .filter(particle -> {
                Vec3 position = particle.getPosition();

                return frustum.pointInFrustum(position.x, position.y, position.z);
            })
            .sorted(Comparator.comparingDouble((AkytheraMeshParticle particle) -> particle.getPosition().distanceToSqr(cameraPosition)).reversed())
            .toList();

        return new State(visible, cameraPosition, partialTickTime);
    }

    /// Frame-updates the enabled [TickableDynamicComponent]s of every [ComponentAttachable] among `particles`, by
    /// `frameTime` seconds. Nothing happens for a zero `frameTime`.
    public static void frameUpdate(final Collection<? extends Particle> particles, final float frameTime, final float partialTick) {
        if (frameTime <= 0.0F) return;

        for (Particle particle : particles) {
            if (!(particle instanceof ComponentAttachable host)) continue;

            host.getComponents().values().forEach(component -> {
                if (component.isEnabled() && component instanceof TickableDynamicComponent<?> dynamic) {
                    dynamic.frameUpdate(frameTime, partialTick);
                }
            });
        }
    }

    /// @param cameraPosition in world space, which the models are drawn relative to
    private record State(List<AkytheraMeshParticle> particles, Vec3 cameraPosition, float partialTick) implements ParticleGroupRenderState {
        @Override
        public void submit(final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
            PoseStack poseStack = new PoseStack();

            for (AkytheraMeshParticle particle : particles) {
                particle.submit(poseStack, submitNodeCollector, cameraPosition, partialTick);
            }
        }
    }
}
