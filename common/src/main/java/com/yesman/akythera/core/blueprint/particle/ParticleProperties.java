package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// The properties every particle type shares, written flat beside the type's own fields.
///
/// @param lifetime   in ticks
/// @param gravity    downward acceleration, in units of 0.04 blocks per tick squared
/// @param friction   the share of velocity kept each tick
/// @param brightness the light the particle gives itself on top of the world's, 0 to 1. 1 draws it full-bright
public record ParticleProperties(boolean hasPhysics, float bbWidth, float bbHeight, int lifetime, float gravity, float friction, float brightness) {
    public static final ParticleProperties DEFAULT = new ParticleProperties(true, 0.2F, 0.2F, 20, 0.0F, 0.98F, 0.0F);

    public static final MapCodec<ParticleProperties> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.BOOL.optionalFieldOf("has_physics", DEFAULT.hasPhysics).forGetter(ParticleProperties::hasPhysics),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("bb_width", DEFAULT.bbWidth).forGetter(ParticleProperties::bbWidth),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("bb_height", DEFAULT.bbHeight).forGetter(ParticleProperties::bbHeight),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("lifetime", DEFAULT.lifetime).forGetter(ParticleProperties::lifetime),
                Codec.FLOAT.optionalFieldOf("gravity", DEFAULT.gravity).forGetter(ParticleProperties::gravity),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("friction", DEFAULT.friction).forGetter(ParticleProperties::friction),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("brightness", DEFAULT.brightness).forGetter(ParticleProperties::brightness)
            ).apply(instance, ParticleProperties::new)
    );
}
