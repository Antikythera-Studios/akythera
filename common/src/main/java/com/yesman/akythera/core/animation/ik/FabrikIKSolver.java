package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Vector3d;

/// Forward And Backward Reaching IK: moves the joints toward the target and back to the root, keeping each bone's
/// length, until the tip is close enough or [#iterations] run out. Solves chains of any length, like tails and whips.
///
/// @param iterations most passes each frame
public record FabrikIKSolver(int iterations) implements IKSolver {
    public static final int DEFAULT_ITERATIONS = 10;

    public static final MapCodec<FabrikIKSolver> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.intRange(1, 100).optionalFieldOf("iterations", DEFAULT_ITERATIONS).forGetter(FabrikIKSolver::iterations)
            ).apply(instance, FabrikIKSolver::new)
    );

    /// How close the tip must come to the target to stop early, in blocks.
    private static final double TOLERANCE = 1.0E-3D;

    @Override
    public IKSolverType type() {
        return AkytheraIKSolverType.FABRIK;
    }

    @Override
    public boolean supportsLength(final int chainLength) {
        return chainLength >= 1;
    }

    @Override
    public void solve(final IKChainKinematics chain, final Vector3d target) {
        int length = chain.length();
        Vector3d[] joints = new Vector3d[length + 1];
        Vector3d root = new Vector3d(chain.joint(0));

        for (int pass = 0; pass < iterations; pass++) {
            if (chain.joint(length).distance(target) < TOLERANCE) return;

            for (int i = 0; i <= length; i++) joints[i] = new Vector3d(chain.joint(i));

            joints[length].set(target);

            for (int i = length - 1; i >= 0; i--) {
                Vector3d along = joints[i].sub(joints[i + 1], new Vector3d());

                if (along.lengthSquared() > 0.0D) joints[i].set(joints[i + 1]).add(along.normalize().mul(chain.boneLength(i)));
            }

            joints[0].set(root);

            for (int i = 0; i < length; i++) {
                Vector3d along = joints[i + 1].sub(joints[i], new Vector3d());

                if (along.lengthSquared() > 0.0D) joints[i + 1].set(joints[i]).add(along.normalize().mul(chain.boneLength(i)));
            }

            for (int i = 0; i < length; i++) chain.aim(i, joints[i + 1]);
        }
    }
}
