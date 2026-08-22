package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.world.entity.Entity;

/// Each animation layer has independent playing sequence and
/// total pose in determined by blending each layer in [Animator]
///
/// Specifically used on combining lower & upper body poses for
/// aiming, blocking shields, etc.
public final class AnimationLayer {
    private final AnimationDriver animDriver;
    private final AnimationMontagePlayer montagePlayer = new AnimationMontagePlayer();

    public AnimationLayer(final AnimationDriver animDriver) {
        if (animDriver == null) {
            throw new IllegalArgumentException("The root animation driver can't be null");
        }

        this.animDriver = animDriver;
    }

    /// Factory exposed as a script function so the Anim Graph's "Anim Layer" node can build a layer
    /// from its base driver and `MAP_PUT` it into the animator's layer map (constructors are not
    /// scanned, only methods — so the editor reaches the constructor through this).
    @ScriptFunction(label = "Create Anim Layer", desc = "Creates an animation layer from a base animation driver", pure = true)
    public static AnimationLayer create(final AnimationDriver animDriver) {
        return new AnimationLayer(animDriver);
    }

    public void tick(final EntityPatch<Entity> owner) {
        animDriver.tick(owner);
        montagePlayer.tick(owner);
    }

    public void update(final float frameTime) {
        animDriver.update(frameTime);
        montagePlayer.update(frameTime);
    }

    public AnimationMontagePlayer montagePlayer() {
        return montagePlayer;
    }

    @ScriptFunction(label = "Get Pose", desc = "Get the animation layer's current pose")
    public Pose getPose() {
        // base layer pose is weighted by (1 - w), as montage pose by (w)
        return PoseOperators.blendPoses(animDriver.getPose(), 1.0F - montagePlayer.getWeight(), montagePlayer.getPose(), montagePlayer.getWeight());
    }
}
