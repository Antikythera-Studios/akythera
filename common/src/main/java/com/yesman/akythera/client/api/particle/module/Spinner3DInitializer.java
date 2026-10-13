package com.yesman.akythera.client.api.particle.module;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.AkytheraMeshParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// Turns a mesh particle about a random axis by a random angle in a range `amount` wide, centered on zero.
///
/// @param amount the range's full width, in degrees. Zero when unset
public record Spinner3DInitializer(
    @ModuleParameter(float.class) PureNode amount
) implements ParticleInitializerBlueprint, ParticleInitializerModule {
    public static final MapCodec<Spinner3DInitializer> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleModuleContext.parameter("amount").forGetter(Spinner3DInitializer::amount)
            ).apply(instance, Spinner3DInitializer::new)
    );

    @Override
    public void initialize(final AkytheraParticle particle) {
        AkytheraMeshParticle mesh = (AkytheraMeshParticle)particle;
        RandomSource random = particle.getRandom();
        float amount = new ParticleModuleContext(particle, "spinner_3d").evaluateFloat(this.amount, 0.0F);
        float angle = (random.nextFloat() - 0.5F) * amount * Mth.DEG_TO_RAD;
        Vector3f axis = new Vector3f((float)random.nextGaussian(), (float)random.nextGaussian(), (float)random.nextGaussian());

        if (axis.lengthSquared() < 1.0E-7F) return;

        mesh.setRotation(new Quaternionf(mesh.getRotation()).rotateAxis(angle, axis.normalize()));
    }

    @Override
    public Class<? extends AkytheraParticle> appliesTo() {
        return AkytheraMeshParticle.class;
    }

    @Override
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        return this;
    }

    @Override
    public MapCodec<Spinner3DInitializer> mapCodec() {
        return MAP_CODEC;
    }
}
