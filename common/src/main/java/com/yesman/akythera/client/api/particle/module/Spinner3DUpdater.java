package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraMeshParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/// Turns a mesh particle about `axis` by `angle` per second.
///
/// @param axis  in its emitter's space. Any length; zero turns nothing. Zero when unset
/// @param angle in degrees per second. Zero when unset
public record Spinner3DUpdater(
    @ModuleParameter(value = Vec3.class, direction = true) PureNode axis,
    @ModuleParameter(float.class) PureNode angle
) implements ParticleUpdaterBlueprint, ParticleUpdateModule {
    public static final MapCodec<Spinner3DUpdater> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("axis").forGetter(Spinner3DUpdater::axis),
                ParticleModuleContext.parameter("angle").forGetter(Spinner3DUpdater::angle)
            ).apply(instance, Spinner3DUpdater::new)
    );

    @Override
    public void update(final AkytheraParticle particle) {
        ParticleModuleContext context = new ParticleModuleContext(particle, "spinner_3d");
        AkytheraMeshParticle mesh = (AkytheraMeshParticle)particle;
        Vec3 axis = particle.getEmitter().getWorldTransform().mulDirection(context.evaluate(this.axis, Vec3.ZERO));
        float angle = context.evaluateFloat(this.angle, 0.0F) * Mth.DEG_TO_RAD / 20.0F;

        if (axis.lengthSqr() < 1.0E-7D) return;

        axis = axis.normalize();
        mesh.setRotation(new Quaternionf().rotationAxis(angle, (float)axis.x, (float)axis.y, (float)axis.z).mul(mesh.getRotation()));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraMeshParticle.class;
    }

    @Override
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<Spinner3DUpdater> mapCodec() {
        return MAP_CODEC;
    }
}
