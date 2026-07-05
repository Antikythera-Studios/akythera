package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/// Drivers that has time simulating function and [Pose] getter
/// based on that simulated time.
///
/// Receives [PlayableAnimation] to load a current playing animation
///
/// The implementation of how to activate and terminate the driver
/// is depending on each inheritance class
public interface AnimationDriver {
    /// Returns a pose by simulated time
    Pose getPose();

    /// Returns a currently loaded animation
    @Nullable PlayableAnimation getPlayingAnimation();

    /// Updates the driver
    ///
    /// @param frameTime Time step to update. Called in each render thread in
    /// client side and fixed tick(0.05) in server side
    void update(float frameTime);

    /// Tick the driver
    ///
    /// Called on each fixed update (0.05 sec)
    default void tick(final EntityPatch<Entity> owner) {
    }

    /// Returns the current frame time being simulated
    float getTime();

    /// Activates the driver with fading-in animation pose
    void start();

    /// Deactivates the driver by fading-out animation pose
    void terminate();

    /// Calculates blending value divided by [PlayableAnimation#fadeInTime] or [PlayableAnimation#fadeInTime].
    ///
    /// The return value gradually increases until 1.0 when being activated, and decreases
    /// until 0.0 when the driver is being deactivated.
    ///
    /// The return value is referred to when blending animations in [BlendingAnimator] (A client side animator).
    /// So it's negligible in server side.
    float getWeight();

    /// Returns whether the driver ends. Won't return `true` if animation is set to `loop`
    /// by [PlayableAnimation#loops].
    ///
    /// Beware the driver being *finished* only when the playback time reaches [PlayableAnimation#getTotalFrameTime]
    /// normally, without any interruption.
    boolean isFinished();
}
