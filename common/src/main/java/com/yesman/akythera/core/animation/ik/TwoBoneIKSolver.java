package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.core.math.Transform;
import org.joml.Vector3d;

/// Solves a two-bone chain exactly, like a leg or an arm. The middle joint stays on the side the pose bends it toward,
/// or goes to the mirrored side when a rotation limit keeps the tip off the target there.
public record TwoBoneIKSolver() implements IKSolver {
    public static final TwoBoneIKSolver INSTANCE = new TwoBoneIKSolver();

    public static final MapCodec<TwoBoneIKSolver> CODEC = MapCodec.unit(INSTANCE);

    /// Distance kept between the target's reach and the chain's fully folded or fully stretched length, in blocks.
    private static final double REACH_MARGIN = 1.0E-4D;

    /// How far the tip may end from the reached point before the mirrored bend side is tried, in blocks.
    private static final double MISS_TOLERANCE = 1.0E-3D;

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
        double upper = chain.boneLength(0);
        double lower = chain.boneLength(1);
        Vector3d toTarget = target.sub(root, new Vector3d());
        double reach = Math.clamp(toTarget.length(), Math.abs(upper - lower) + REACH_MARGIN, upper + lower - REACH_MARGIN);

        if (toTarget.lengthSquared() == 0.0D || upper == 0.0D || reach <= 0.0D) return;

        Vector3d direction = toTarget.normalize(new Vector3d());
        Vector3d bend = middle.sub(root, new Vector3d());
        bend.sub(direction.mul(bend.dot(direction), new Vector3d()));

        if (bend.lengthSquared() < REACH_MARGIN * REACH_MARGIN) {
            bend = direction.cross(Math.abs(direction.y) < 0.9D ? new Vector3d(0.0D, 1.0D, 0.0D) : new Vector3d(1.0D, 0.0D, 0.0D), new Vector3d());
        }

        bend.normalize();

        double cosine = Math.clamp((upper * upper + reach * reach - lower * lower) / (2.0D * upper * reach), -1.0D, 1.0D);
        Vector3d reached = root.add(direction.mul(reach, new Vector3d()), new Vector3d());
        Transform[] start = chain.snapshot();

        bendToward(chain, root, direction, upper, cosine, bend, reached);

        double miss = chain.joint(2).distance(reached);

        if (miss <= MISS_TOLERANCE) return;

        Transform[] bent = chain.snapshot();
        chain.restore(start);
        bendToward(chain, root, direction, upper, cosine, bend.negate(new Vector3d()), reached);

        if (chain.joint(2).distance(reached) >= miss) chain.restore(bent);
    }

    /// Aims the chain at `reached` with the middle joint pushed off the root-to-target line along `bend`.
    private static void bendToward(
        final IKChainKinematics chain, final Vector3d root, final Vector3d direction, final double upper, final double cosine,
        final Vector3d bend, final Vector3d reached
    ) {
        Vector3d newMiddle = new Vector3d(root)
            .add(direction.mul(upper * cosine, new Vector3d()))
            .add(bend.mul(upper * Math.sqrt(1.0D - cosine * cosine), new Vector3d()));

        chain.aim(0, newMiddle);
        chain.aim(1, reached);
    }
}
