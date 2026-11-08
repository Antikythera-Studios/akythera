package com.yesman.akythera.api.world.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/// The senses of a brain running a behavior tree asset. Each stimulation runs the matching event of the asset's
/// event graph. A sense left empty is one the owner does not have.
public record PerceptionSettings(Optional<Sight> sight, Optional<Hearing> hearing, Optional<Vibration> vibration, Optional<Touch> touch) {
    public static final PerceptionSettings NONE = new PerceptionSettings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<PerceptionSettings> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Sight.CODEC.optionalFieldOf("sight").forGetter(PerceptionSettings::sight),
                Hearing.CODEC.optionalFieldOf("hearing").forGetter(PerceptionSettings::hearing),
                Vibration.CODEC.optionalFieldOf("vibration").forGetter(PerceptionSettings::vibration),
                Touch.CODEC.optionalFieldOf("touch").forGetter(PerceptionSettings::touch)
            ).apply(instance, PerceptionSettings::new)
    );

    /// Sees the entities inside `shape`, placed at the owner's eyes and turned by its head.
    ///
    /// @param shape       a sphere or a cone
    /// @param lineOfSight whether an entity also needs a clear line from the eyes, so one behind a wall is not seen
    public record Sight(Collider shape, boolean lineOfSight) {
        public static final Codec<Sight> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Collider.MAP_CODEC.codec().validate(collider ->
                        collider.type() == AkytheraColliderType.SPHERE || collider.type() == AkytheraColliderType.CONE
                            ? DataResult.success(collider)
                            : DataResult.error(() -> "Sight is a sphere or a cone, not " + collider.type())
                    ).fieldOf("shape").forGetter(Sight::shape),
                    Codec.BOOL.optionalFieldOf("line_of_sight", true).forGetter(Sight::lineOfSight)
                ).apply(instance, Sight::new)
        );
    }

    /// Hears sounds that reach a sphere around the owner.
    ///
    /// @param minVolume the quietest heard volume, after the falloff over the distance to the sphere
    /// @param radius    the radius of the sphere, in blocks. Zero hears at the owner's position only
    public record Hearing(float minVolume, float radius) {
        /// The largest [#radius], which bounds the search for the brains a sound reaches.
        public static final float MAX_RADIUS = 32.0F;

        public static final Codec<Hearing> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("min_volume", 0.0F).forGetter(Hearing::minVolume),
                    Codec.floatRange(0.0F, MAX_RADIUS).optionalFieldOf("radius", 0.0F).forGetter(Hearing::radius)
                ).apply(instance, Hearing::new)
        );
    }

    /// Feels game events within their notification radius.
    public record Vibration() {
        public static final Codec<Vibration> CODEC = MapCodec.unit(Vibration::new).codec();
    }

    /// Feels pushes and damage.
    ///
    /// @param pushCooldown game ticks a pushing entity is not felt again for
    public record Touch(int pushCooldown) {
        public static final Codec<Touch> CODEC = ExtraCodecs.NON_NEGATIVE_INT
            .optionalFieldOf("push_cooldown", 10)
            .xmap(Touch::new, Touch::pushCooldown)
            .codec();
    }
}
