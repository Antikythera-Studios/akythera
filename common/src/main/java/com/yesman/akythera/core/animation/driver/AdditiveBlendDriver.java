package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.AnimationLoader;
import com.yesman.akythera.core.util.helper.PoseOperators;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// Lays an additive pose over a base pose by a live alpha. [AbstractBlendDriver#blend] carries deltas
/// rather than a pose of its own, so the animation behind it must be imported as
/// [AnimationLoader.ImportType#ADDITIVE]; the base is what the mix reads as, whatever the alpha.
///
/// Deltas in [AnimationLoader.AdditiveSpace#MESH] are laid on through the armature of the last [#tick]'s owner.
public final class AdditiveBlendDriver extends AbstractBlendDriver {
    private final Supplier<Float> alphaUpdater;

    /// The owner of the last [#tick], whose armature lays mesh space deltas on.
    private @Nullable ComponentAttachable owner;

    /// @param alphaUpdater Wire alpha value provider, in [0, 1]
    public AdditiveBlendDriver(final AnimationDriver base, final AnimationDriver additive, final Supplier<Float> alphaUpdater) {
        super(base, additive);

        this.alphaUpdater = alphaUpdater;
    }

    @Override
    public Pose getPose() {
        float alpha = Math.clamp(alphaUpdater.get(), 0.0F, 1.0F);
        PlayableAnimation additive = blend.getPlayingAnimation();
        ArmatureComponent armatureComponent = owner == null ? null : ArmatureComponent.armatureOf(owner);
        Armature armature = armatureComponent == null || armatureComponent.getArmature() == null ? null : armatureComponent.getArmature().get();

        if (additive != null && additive.additiveSpace() == AnimationLoader.AdditiveSpace.MESH && armature != null) {
            return PoseOperators.meshSpaceAdditivePose(armature, base.getPose(), blend.getPose(), alpha);
        }

        return PoseOperators.additivePose(base.getPose(), blend.getPose(), alpha);
    }

    @Override
    public void tick(final ComponentAttachable owner) {
        super.tick(owner);
        this.owner = owner;
    }

    @Override
    protected AnimationDriver dominant() {
        return blend;
    }
}
