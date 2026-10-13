package com.yesman.akythera.core.blueprint.particle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraSpriteParticle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.List;

/// A camera-facing quad animating through its sprites over its lifetime, read from the particle atlas.
///
/// @param size    the quad's half extent, in blocks
/// @param roll    the quad's rotation about the view axis, in degrees
/// @param sprites particle atlas sprite ids in animation order, or a vanilla particle type whose sprites the quad
///                borrows
public record SpriteParticleBlueprint(
    ParticleProperties properties,
    float size,
    RGBA color,
    float roll,
    Either<List<Identifier>, Identifier> sprites
) implements ParticleBlueprint {
    /// A vanilla particle type, written `{"vanilla": "<id>"}`. Only its sprites are used.
    public static final Codec<Identifier> VANILLA_PARTICLE = Identifier.CODEC
        .validate(id -> BuiltInRegistries.PARTICLE_TYPE.containsKey(id)
            ? DataResult.success(id)
            : DataResult.error(() -> "No vanilla particle type " + id))
        .fieldOf("vanilla")
        .codec();

    public static final MapCodec<SpriteParticleBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleProperties.MAP_CODEC.forGetter(SpriteParticleBlueprint::properties),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("size", 0.2F).forGetter(SpriteParticleBlueprint::size),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(SpriteParticleBlueprint::color),
                Codec.FLOAT.optionalFieldOf("roll", 0.0F).forGetter(SpriteParticleBlueprint::roll),
                Codec.either(Identifier.CODEC.listOf(1, Integer.MAX_VALUE), VANILLA_PARTICLE).fieldOf("sprites").forGetter(SpriteParticleBlueprint::sprites)
            ).apply(instance, SpriteParticleBlueprint::new)
    );

    /// The particle an emitter naming the vanilla particle type `id` spawns: default properties, the type's sprites.
    public static SpriteParticleBlueprint ofVanilla(final Identifier id) {
        return new SpriteParticleBlueprint(ParticleProperties.DEFAULT, 0.2F, new RGBA(1.0F, 1.0F, 1.0F), 0.0F, Either.right(id));
    }

    /// @throws IllegalStateException when the sprites name a vanilla particle type that has no sprites
    @Override
    public AkytheraParticle instantiate(final AkytheraParticleEmitter emitter) {
        return new AkytheraSpriteParticle(emitter, this);
    }

    @Override
    public MapCodec<SpriteParticleBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
