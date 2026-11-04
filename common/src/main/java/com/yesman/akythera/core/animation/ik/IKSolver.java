package com.yesman.akythera.core.animation.ik;

import com.mojang.serialization.Codec;
import org.joml.Vector3d;

/// How the bones of an [IKChain] turn so its tip reaches a target.
public interface IKSolver {
    Codec<IKSolver> CODEC = IKSolverType.ENUM_MANAGER.getNameCodec().dispatch("type", IKSolver::type, IKSolverType::mapCodec);

    IKSolverType type();

    /// Whether the solver handles a chain of `chainLength` bones.
    boolean supportsLength(int chainLength);

    /// Turns the bones of `chain` so its tip reaches `target`, in armature space, or comes as close as it can.
    void solve(IKChainKinematics chain, Vector3d target);
}
