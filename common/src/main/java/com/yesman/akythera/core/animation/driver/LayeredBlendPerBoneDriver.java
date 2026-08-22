package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.util.helper.PoseOperators;

/// Hands one branch of the skeleton over to a second pose: bones from [#boneName] down ramp toward
/// [AbstractBlendDriver#blend] over [#depth] bones, the rest of the skeleton stays on
/// [AbstractBlendDriver#base].
///
/// The armature is read from [#animator] on every sample, so the mix follows the owner's current
/// armature rather than the one it was constructed against.
public final class LayeredBlendPerBoneDriver extends AbstractBlendDriver {
    private final Animator animator;
    private final String boneName;
    private final int depth;

    public LayeredBlendPerBoneDriver(
        final Animator animator, final AnimationDriver base, final AnimationDriver layered,
        final String boneName, final int depth
    ) {
        super(base, layered);

        this.animator = animator;
        this.boneName = boneName;
        this.depth = depth;
    }

    @Override
    public Pose getPose() {
        return PoseOperators.layeredBlendPerBone(base.getPose(), blend.getPose(), boneName, depth, animator.getArmature());
    }

    @Override
    protected AnimationDriver dominant() {
        return blend;
    }
}
