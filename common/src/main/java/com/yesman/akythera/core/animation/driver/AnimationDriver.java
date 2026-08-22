package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
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
    @ScriptFunction(pure = true, label = "Get Time", desc = "Playback time the driver has simulated so far, in seconds")
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
    /// The return value is referred to when blending animationsToReplace in [BlendingAnimator]. (Client side animator)
    /// So it's negligible in server side.
    @ScriptFunction(pure = true, label = "Get Weight", desc = "Fade weight, rising to 1.0 while fading in and falling to 0.0 while fading out")
    float getWeight();

    /// Returns whether the driver ends. Won't return `true` if animation is set to `loop`
    /// by [PlayableAnimation#loops].
    ///
    /// Beware the driver being *finished* only when the playback time reaches [PlayableAnimation#getTotalFrameTime]
    /// normally, without any interruption.
    @ScriptFunction(pure = true, label = "Is Finished", desc = "Whether the animation played out to its end. Always false for a looping animation")
    boolean isFinished();

    /// Returns the driver's fading mode. Returns `true` when first call of [#start] and full weighted (1.0), `false`
    /// when the driver is pending to be terminated by [#terminate].
    @ScriptFunction(pure = true, label = "Is Fading In", desc = "True while the driver is starting or held at full weight, false once it is terminating")
    boolean fadingInOrOut();

    /// Returns a current fading in/out time.
    float getFadeTime();

    /// Synchronize the driver's playback time with `source` driver.
    void syncDriverTo(@NonNull AnimationDriver source);
}
