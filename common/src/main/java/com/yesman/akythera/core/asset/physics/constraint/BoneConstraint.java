package com.yesman.akythera.core.asset.physics.constraint;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.math.Transform;

/// A rule that rewrites one bone's pose transform, like a Blender bone constraint in local space.
///
/// A bone's constraints run in their order, and the bones in hierarchy order.
///
/// @see Armature#applyBoneConstraints
public interface BoneConstraint {
    Codec<BoneConstraint> CODEC = BoneConstraintType.ENUM_MANAGER.getNameCodec().dispatch("type", BoneConstraint::type, BoneConstraintType::mapCodec);

    BoneConstraintType type();

    /// Whether the constraint holds a rag doll alone and leaves animated poses as they are.
    boolean simulationOnly();

    /// Whether the constraint rewrites a pose a rag doll made (`simulated`), or one an animation made.
    default boolean posesWhen(final boolean simulated) {
        return simulated || !simulationOnly();
    }

    /// The pose transform the owner bone takes, from its current `transform` and the rest of `pose`.
    Transform apply(Transform transform, Pose pose);
}
