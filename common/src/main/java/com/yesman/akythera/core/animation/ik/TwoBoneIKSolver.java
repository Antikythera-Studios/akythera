package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.util.StringRepresentable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

/// Solves a two-bone chain exactly, like a leg or an arm, bending it in the plane the pose bends in. A straight pose
/// bends the way [#straightBend] names. The mirrored side is tried when a rotation limit keeps the tip off the target.
///
/// @param straightBend which way a straight chain bends around its middle bone's X axis
public record TwoBoneIKSolver(StraightBend straightBend) implements IKSolver {
    public static final TwoBoneIKSolver DEFAULT = new TwoBoneIKSolver(StraightBend.NEGATIVE_X);

    public static final MapCodec<TwoBoneIKSolver> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                StraightBend.CODEC.optionalFieldOf("straight_bend", DEFAULT.straightBend).forGetter(TwoBoneIKSolver::straightBend)
            ).apply(instance, TwoBoneIKSolver::new)
    );

    /// Distance kept between the target's reach and the chain's fully folded or fully stretched length, in blocks.
    private static final double REACH_MARGIN = 1.0E-4D;

    /// How far the middle joint stands off the root-to-tip line at least for the pose to count as bent, in blocks.
    private static final double STRAIGHT_MARGIN = 1.0E-3D;

    /// How far the tip may end from the reached point before the mirrored bend side is tried, in blocks.
    private static final double MISS_TOLERANCE = 1.0E-3D;

    /// Most bend passes one side takes.
    private static final int BEND_PASSES = 4;

    /// How close the tip must come to the reached point to end the bend passes, in blocks.
    private static final double BEND_TOLERANCE = 1.0E-5D;

    @Override
    public IKSolverType type() {
        return AkytheraIKSolverType.TWO_BONE;
    }

    @Override
    public boolean supportsLength(final int chainLength) {
        return chainLength == 2;
    }

    @Override
    public void solve(final IKChainKinematics chain, final Vector3d target) {
        Vector3d root = new Vector3d(chain.joint(0));
        Vector3d middle = new Vector3d(chain.joint(1));
        Vector3d tip = new Vector3d(chain.joint(2));
        Vector3d along = tip.sub(root, new Vector3d());

        if (target.distanceSquared(root) == 0.0D || along.lengthSquared() == 0.0D || chain.boneLength(0) == 0.0D || chain.boneLength(1) == 0.0D) return;

        Vector3d side = middle.sub(root, new Vector3d());

        along.normalize();
        side.sub(along.mul(side.dot(along), new Vector3d()));

        if (side.lengthSquared() < STRAIGHT_MARGIN * STRAIGHT_MARGIN) {
            side = chain.boneAxis(1, new Vector3d(1.0D, 0.0D, 0.0D)).cross(tip.sub(middle, new Vector3d())).mul(straightBend.sign);
            side.sub(along.mul(side.dot(along), new Vector3d()));
        }

        if (side.lengthSquared() < REACH_MARGIN * REACH_MARGIN) {
            side = along.cross(Math.abs(along.y) < 0.9D ? new Vector3d(0.0D, 1.0D, 0.0D) : new Vector3d(1.0D, 0.0D, 0.0D), new Vector3d());
        }

        Vector3d hinge = along.cross(side, new Vector3d()).normalize();
        Transform[] start = chain.snapshot();
        double miss = bend(chain, target, hinge);

        if (miss <= MISS_TOLERANCE) return;

        Transform[] bent = chain.snapshot();
        chain.restore(start);

        if (bend(chain, target, hinge.negate(new Vector3d())) >= miss) chain.restore(bent);
    }

    /// Bends the chain around `hinge` toward `target`, returning how far the tip ends from what it can reach.
    private static double bend(final IKChainKinematics chain, final Vector3d target, final Vector3d hinge) {
        Vector3d axis = new Vector3d(hinge);
        double miss = Double.POSITIVE_INFINITY;

        for (int pass = 0; pass < BEND_PASSES && miss > BEND_TOLERANCE; pass++) {
            Vector3d root = new Vector3d(chain.joint(0));
            Vector3d along = chain.joint(2).sub(root, new Vector3d()).normalize();
            Vector3d toTarget = target.sub(root, new Vector3d());
            double upper = chain.boneLength(0);
            double lower = chain.boneLength(1);
            double reach = Math.clamp(toTarget.length(), Math.abs(upper - lower) + REACH_MARGIN, upper + lower - REACH_MARGIN);
            Vector3d reached = toTarget.normalize().mul(reach).add(root);
            double cosine = Math.clamp((upper * upper + reach * reach - lower * lower) / (2.0D * upper * reach), -1.0D, 1.0D);
            Vector3d middle = new Quaterniond().rotationAxis(Math.acos(cosine), axis.x, axis.y, axis.z).transform(new Vector3d(along)).mul(upper);

            chain.rotate(0, turnAround(axis, chain.joint(1).sub(root, new Vector3d()), middle));

            Vector3d tip = along.mul(reach, new Vector3d()).add(root);

            chain.rotate(1, turnAround(axis, chain.joint(2).sub(chain.joint(1), new Vector3d()), tip.sub(chain.joint(1))));

            Quaterniond swing = new Quaterniond().rotationTo(
                chain.joint(2).sub(root, new Vector3d()).normalize(), reached.sub(root, new Vector3d()).normalize()
            );

            chain.rotate(0, swing);
            swing.transform(axis);
            miss = chain.joint(2).distance(reached);
        }

        return miss;
    }

    /// The turn around `axis` that brings `from` onto `to`, both perpendicular to `axis`.
    private static Quaterniond turnAround(final Vector3d axis, final Vector3d from, final Vector3d to) {
        double angle = Math.atan2(axis.dot(from.cross(to, new Vector3d())), from.dot(to));

        return new Quaterniond().rotationAxis(angle, axis.x, axis.y, axis.z);
    }

    /// Which way a straight chain bends around its middle bone's X axis: the sign of the middle bone's turn.
    public enum StraightBend implements StringRepresentable {
        NEGATIVE_X("negative_x", 1.0D),
        POSITIVE_X("positive_x", -1.0D);

        public static final Codec<StraightBend> CODEC = StringRepresentable.fromEnum(StraightBend::values);

        private final String name;

        /// The sign that puts the middle joint on this bend's side of `X x lower bone`.
        private final double sign;

        StraightBend(final String name, final double sign) {
            this.name = name;
            this.sign = sign;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
