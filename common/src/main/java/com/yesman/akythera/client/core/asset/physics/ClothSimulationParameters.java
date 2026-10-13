package com.yesman.akythera.client.core.asset.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// Global parameters of one cloth simulation. Lengths are in blocks, times in seconds.
///
/// @param substeps               solver substeps per rendered frame, 1–32
/// @param gravityScale           multiplier on 9.81 blocks/s² downward
/// @param damping                velocity lost per second, as a fraction
/// @param stretchCompliance      XPBD compliance of edge constraints; 0 is inextensible
/// @param bendCompliance         XPBD compliance of the across-edge bending constraints
/// @param tetherScale            how far past its rest geodesic distance a particle may stray from its anchor, as a ratio (≥ 1)
/// @param maxDistance            how far a weight-1 particle may drift from its animated position
/// @param collisionThickness     gap kept between cloth and colliders
/// @param friction               fraction of tangential motion removed on collider contact, 0–1
/// @param selfCollision          whether particles push each other apart
/// @param selfCollisionThickness minimum distance between two particles under self-collision
/// @param linearInertiaScale     how much of the owner's translation the cloth resists, 0–1
/// @param angularInertiaScale    how much of the owner's rotation the cloth resists, 0–1
public record ClothSimulationParameters(
    int substeps,
    float gravityScale,
    float damping,
    float stretchCompliance,
    float bendCompliance,
    float tetherScale,
    float maxDistance,
    float collisionThickness,
    float friction,
    boolean selfCollision,
    float selfCollisionThickness,
    float linearInertiaScale,
    float angularInertiaScale
) {
    public static final ClothSimulationParameters DEFAULT = new ClothSimulationParameters(
        8, 1.0F, 0.5F, 0.0F, 0.001F, 1.0F, 1.0F, 0.03F, 0.1F, false, 0.02F, 1.0F, 1.0F
    );

    public static final Codec<ClothSimulationParameters> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.intRange(1, 32).optionalFieldOf("substeps", DEFAULT.substeps).forGetter(ClothSimulationParameters::substeps),
                Codec.FLOAT.optionalFieldOf("gravity_scale", DEFAULT.gravityScale).forGetter(ClothSimulationParameters::gravityScale),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("damping", DEFAULT.damping).forGetter(ClothSimulationParameters::damping),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("stretch_compliance", DEFAULT.stretchCompliance).forGetter(ClothSimulationParameters::stretchCompliance),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("bend_compliance", DEFAULT.bendCompliance).forGetter(ClothSimulationParameters::bendCompliance),
                Codec.floatRange(1.0F, Float.MAX_VALUE).optionalFieldOf("tether_scale", DEFAULT.tetherScale).forGetter(ClothSimulationParameters::tetherScale),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("max_distance", DEFAULT.maxDistance).forGetter(ClothSimulationParameters::maxDistance),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("collision_thickness", DEFAULT.collisionThickness).forGetter(ClothSimulationParameters::collisionThickness),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("friction", DEFAULT.friction).forGetter(ClothSimulationParameters::friction),
                Codec.BOOL.optionalFieldOf("self_collision", DEFAULT.selfCollision).forGetter(ClothSimulationParameters::selfCollision),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("self_collision_thickness", DEFAULT.selfCollisionThickness).forGetter(ClothSimulationParameters::selfCollisionThickness),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("linear_inertia_scale", DEFAULT.linearInertiaScale).forGetter(ClothSimulationParameters::linearInertiaScale),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("angular_inertia_scale", DEFAULT.angularInertiaScale).forGetter(ClothSimulationParameters::angularInertiaScale)
            ).apply(instance, ClothSimulationParameters::new)
    );
}
