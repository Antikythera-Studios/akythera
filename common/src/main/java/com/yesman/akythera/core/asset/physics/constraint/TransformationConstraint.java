package com.yesman.akythera.core.asset.physics.constraint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Maps one channel of a target bone's pose onto one channel of the owner's, like Blender's Transformation constraint
/// with both bones in local space: each owner axis reads one source axis, whose range maps linearly onto the owner
/// axis's range.
///
/// @param target         the bone whose pose is read
/// @param from           what is read off [#target]'s pose
/// @param to             what is written to the owner's pose
/// @param extrapolate    whether a source value outside its range maps past the owner's range, rather than to its end
/// @param mix            how the mapped values combine with the owner's own
/// @param simulationOnly whether animated poses are left as they are
public record TransformationConstraint(String target, MapFrom from, MapTo to, boolean extrapolate, Mix mix, boolean simulationOnly)
    implements BoneConstraint
{
    public static final MapCodec<TransformationConstraint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("target").forGetter(TransformationConstraint::target),
                MapFrom.CODEC.optionalFieldOf("map_from", MapFrom.DEFAULT).forGetter(TransformationConstraint::from),
                MapTo.CODEC.optionalFieldOf("map_to", MapTo.DEFAULT).forGetter(TransformationConstraint::to),
                Codec.BOOL.optionalFieldOf("extrapolate", false).forGetter(TransformationConstraint::extrapolate),
                Mix.CODEC.optionalFieldOf("mix", Mix.REPLACE).forGetter(TransformationConstraint::mix),
                Codec.BOOL.optionalFieldOf("simulation_only", false).forGetter(TransformationConstraint::simulationOnly)
            ).apply(instance, TransformationConstraint::new)
    );

    @Override
    public BoneConstraintType type() {
        return AkytheraBoneConstraintType.TRANSFORMATION;
    }

    @Override
    public Transform apply(final Transform transform, final Pose pose) {
        Vector3d source = from.channel().read(pose.orElseIdentity(target));
        Vector3d mapped = mix == Mix.REPLACE ? to.channel().read(transform) : new Vector3d(to.channel().neutral);
        Range[] ranges = {from.x(), from.y(), from.z()};
        AxisMapping[] axes = {to.x(), to.y(), to.z()};

        for (int i = 0; i < 3; i++) {
            Axis sourceAxis = axes[i].source();

            if (sourceAxis == null) continue;

            Range range = ranges[sourceAxis.ordinal()];
            double value = sourceAxis.of(source);
            double span = range.max() - range.min();

            if (!extrapolate) value = Math.clamp(value, Math.min(range.min(), range.max()), Math.max(range.min(), range.max()));

            mapped.setComponent(i, Mth.lerp(span == 0.0D ? 0.0D : (value - range.min()) / span, axes[i].min(), axes[i].max()));
        }

        return to.channel().write(transform, mapped, mix);
    }

    /// The channels of a pose transform, each per axis in its unit: location in blocks, rotation in degrees of
    /// [QuaternionUtils#toSwingTwist], scale as a ratio.
    public enum Channel implements StringRepresentable {
        LOCATION("location", 0.0D),
        ROTATION("rotation", 0.0D),
        SCALE("scale", 1.0D);

        public static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);

        private final String name;

        /// The value per axis that changes nothing under [Mix#ADD].
        private final double neutral;

        Channel(final String name, final double neutral) {
            this.name = name;
            this.neutral = neutral;
        }

        public Vector3d read(final Transform transform) {
            return switch (this) {
                case LOCATION -> new Vector3d(transform.loc().x, transform.loc().y, transform.loc().z);
                case ROTATION -> {
                    Vector3d radians = QuaternionUtils.toSwingTwist(transform.rot());
                    yield new Vector3d(Math.toDegrees(radians.x), Math.toDegrees(radians.y), Math.toDegrees(radians.z));
                }
                case SCALE -> new Vector3d(transform.sca().x, transform.sca().y, transform.sca().z);
            };
        }

        /// `transform` with this channel replaced by `values`, or combined with them under [Mix#ADD].
        public Transform write(final Transform transform, final Vector3d values, final Mix mix) {
            boolean add = mix == Mix.ADD;

            return switch (this) {
                case LOCATION -> new Transform(
                    add ? transform.loc().add(values.x, values.y, values.z) : new Vec3(values.x, values.y, values.z), transform.rot(), transform.sca()
                );
                case ROTATION -> {
                    Quaterniond rotation = QuaternionUtils.fromSwingTwist(Math.toRadians(values.x), Math.toRadians(values.y), Math.toRadians(values.z));
                    yield new Transform(transform.loc(), add ? transform.rot().mul(rotation, new Quaterniond()) : rotation, transform.sca());
                }
                case SCALE -> new Transform(
                    transform.loc(), transform.rot(), add ? transform.sca().multiply(values.x, values.y, values.z) : new Vec3(values.x, values.y, values.z)
                );
            };
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    public enum Axis implements StringRepresentable {
        X("x"),
        Y("y"),
        Z("z");

        public static final Codec<Axis> CODEC = StringRepresentable.fromEnum(Axis::values);

        private final String name;

        Axis(final String name) {
            this.name = name;
        }

        public double of(final Vector3d vector) {
            return vector.get(ordinal());
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    /// How mapped values combine with the owner's own.
    public enum Mix implements StringRepresentable {
        /// The mapped axes replace the owner's.
        REPLACE("replace"),

        /// The mapped location is added, the mapped rotation turns after the owner's, and the mapped scale multiplies.
        ADD("add");

        public static final Codec<Mix> CODEC = StringRepresentable.fromEnum(Mix::values);

        private final String name;

        Mix(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    /// The range of one source axis, in its channel's unit.
    public record Range(float min, float max) {
        public static final Range ZERO = new Range(0.0F, 0.0F);

        public static final Codec<Range> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Codec.FLOAT.optionalFieldOf("min", 0.0F).forGetter(Range::min),
                    Codec.FLOAT.optionalFieldOf("max", 0.0F).forGetter(Range::max)
                ).apply(instance, Range::new)
        );
    }

    /// What is read off the target's pose.
    ///
    /// @param x the range of the source X axis, and likewise [#y] and [#z]
    public record MapFrom(Channel channel, Range x, Range y, Range z) {
        public static final MapFrom DEFAULT = new MapFrom(Channel.ROTATION, Range.ZERO, Range.ZERO, Range.ZERO);

        public static final Codec<MapFrom> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Channel.CODEC.optionalFieldOf("channel", Channel.ROTATION).forGetter(MapFrom::channel),
                    Range.CODEC.optionalFieldOf("x", Range.ZERO).forGetter(MapFrom::x),
                    Range.CODEC.optionalFieldOf("y", Range.ZERO).forGetter(MapFrom::y),
                    Range.CODEC.optionalFieldOf("z", Range.ZERO).forGetter(MapFrom::z)
                ).apply(instance, MapFrom::new)
        );
    }

    /// The source axis one owner axis reads, and the range the source range maps onto, in the owner channel's unit.
    ///
    /// @param source the source axis, null to leave the owner axis as it is
    public record AxisMapping(@Nullable Axis source, float min, float max) {
        public static final AxisMapping NONE = new AxisMapping(null, 0.0F, 0.0F);

        public static final Codec<AxisMapping> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Axis.CODEC.optionalFieldOf("source").forGetter(ParseUtils.provideOptional(AxisMapping::source)),
                    Codec.FLOAT.optionalFieldOf("min", 0.0F).forGetter(AxisMapping::min),
                    Codec.FLOAT.optionalFieldOf("max", 0.0F).forGetter(AxisMapping::max)
                ).apply(instance, (source, min, max) -> new AxisMapping(source.orElse(null), min, max))
        );
    }

    /// What is written to the owner's pose.
    ///
    /// @param x the mapping of the owner's X axis, and likewise [#y] and [#z]
    public record MapTo(Channel channel, AxisMapping x, AxisMapping y, AxisMapping z) {
        public static final MapTo DEFAULT = new MapTo(Channel.ROTATION, AxisMapping.NONE, AxisMapping.NONE, AxisMapping.NONE);

        public static final Codec<MapTo> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Channel.CODEC.optionalFieldOf("channel", Channel.ROTATION).forGetter(MapTo::channel),
                    AxisMapping.CODEC.optionalFieldOf("x", AxisMapping.NONE).forGetter(MapTo::x),
                    AxisMapping.CODEC.optionalFieldOf("y", AxisMapping.NONE).forGetter(MapTo::y),
                    AxisMapping.CODEC.optionalFieldOf("z", AxisMapping.NONE).forGetter(MapTo::z)
                ).apply(instance, MapTo::new)
        );
    }
}
