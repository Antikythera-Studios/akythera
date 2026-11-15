package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.component.impl.OverlayRendererComponent;
import com.yesman.akythera.client.api.particle.AkytheraOverlayParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.ParticleHost;
import com.yesman.akythera.core.Akythera;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/// A second draw of the model of the entity its system is attached to, over the model's own surface. Spawned only
/// where [ParticleHost#overlayRenderer] answers an [OverlayRendererComponent].
///
/// @param lifetime   in ticks, or [#ENDLESS]
/// @param brightness the light the overlay gives itself on top of the model's, 0 to 1. 1 draws it full-bright
/// @param texture    the texture laid over the model by the model's own UVs. Texels below 0.1 alpha are not drawn
/// @param color      multiplies the texture. Its alpha is how much of the overlay shows, except under
///                   [Blend#MULTIPLY]
/// @param uvScale    how many times the texture repeats over the model's UVs
/// @param uvRotation how far the texture is turned on the model's UVs, in degrees
/// @param uScroll    how far the texture slides along U, in texture lengths per second
/// @param vScroll    how far the texture slides along V, in texture lengths per second
public record OverlayParticleBlueprint(
    int lifetime,
    float brightness,
    Identifier texture,
    RGBA color,
    Blend blend,
    float uvScale,
    float uvRotation,
    float uScroll,
    float vScroll
) implements ParticleBlueprint {
    public static final int ENDLESS = -1; // a lifetime that never ends

    public static final Identifier WHITE_TEXTURE = Akythera.identifier("textures/common/white.png"); // a flat tint

    public static final MapCodec<OverlayParticleBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.INT
                    .validate(lifetime -> lifetime == ENDLESS || lifetime >= 1
                        ? DataResult.success(lifetime)
                        : DataResult.error(() -> "An overlay's lifetime is " + ENDLESS + " or at least 1, not " + lifetime))
                    .optionalFieldOf("lifetime", ParticleProperties.DEFAULT.lifetime())
                    .forGetter(OverlayParticleBlueprint::lifetime),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("brightness", ParticleProperties.DEFAULT.brightness()).forGetter(OverlayParticleBlueprint::brightness),
                Identifier.CODEC.optionalFieldOf("texture", WHITE_TEXTURE).forGetter(OverlayParticleBlueprint::texture),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(OverlayParticleBlueprint::color),
                Blend.CODEC.optionalFieldOf("blend", Blend.TRANSLUCENT).forGetter(OverlayParticleBlueprint::blend),
                Codec.FLOAT.optionalFieldOf("uv_scale", 1.0F).forGetter(OverlayParticleBlueprint::uvScale),
                Codec.FLOAT.optionalFieldOf("uv_rotation", 0.0F).forGetter(OverlayParticleBlueprint::uvRotation),
                Codec.FLOAT.optionalFieldOf("u_scroll", 0.0F).forGetter(OverlayParticleBlueprint::uScroll),
                Codec.FLOAT.optionalFieldOf("v_scroll", 0.0F).forGetter(OverlayParticleBlueprint::vScroll)
            ).apply(instance, OverlayParticleBlueprint::new)
    );

    /// An overlay has no body: no physics, no bounding box, no gravity and no friction.
    @Override
    public ParticleProperties properties() {
        return new ParticleProperties(false, 0.0F, 0.0F, lifetime, 0.0F, 1.0F, brightness);
    }

    /// @throws IllegalStateException when the emitter's system has nothing to draw over
    @Override
    public AkytheraParticle instantiate(final AkytheraParticleEmitter emitter) {
        return new AkytheraOverlayParticle(emitter, this);
    }

    @Override
    public MapCodec<OverlayParticleBlueprint> mapCodec() {
        return MAP_CODEC;
    }

    /// How an overlay's color meets the model's under it.
    public enum Blend implements StringRepresentable {
        TRANSLUCENT, // covers the model by the overlay's alpha
        ADDITIVE, // adds the overlay's color scaled by its alpha
        GLINT, // adds the overlay's color scaled by itself, as an enchanted item's glint
        MULTIPLY; // multiplies the model's color by the overlay's, as a color filter. Its alpha is not used

        public static final Codec<Blend> CODEC = StringRepresentable.fromEnum(Blend::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
