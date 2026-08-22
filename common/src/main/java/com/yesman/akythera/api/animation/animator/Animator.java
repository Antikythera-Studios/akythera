package com.yesman.akythera.api.animation.animator;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAnimMontage;
import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// A component that update each animation player and calculates total pose by blending
/// a pose from each animation player
public interface Animator {
    /// Plays an [AnimationMontage].
    /// Ensure synchronization if it's called in a server side
    default AnimationMontagePlayer.MontageCallbackHandle playAnimMontage(
        @NonNull final ResourceKey<@NonNull AnimationMontage> animMontageKey
    ) {
        return playAnimMontage(animMontageKey, AnimationMontage.GENERIC_SECTION_NAME);
    }

    /// Plays an [AnimationMontage] starting in a specific section
    /// Ensure synchronization if it's called in a server side
    default AnimationMontagePlayer.@Nullable MontageCallbackHandle playAnimMontage(
        @NonNull final ResourceKey<@NonNull AnimationMontage> animMontageKey, String section
    ) {
        // Can't play montage for non-entity object.
        var montageOptional = getOwner().getLevel().registryAccess().get(animMontageKey);

        if (montageOptional.isPresent()) {
            Holder.Reference<AnimationMontage> animMontage = montageOptional.get();
            AnimationMontagePlayer.MontageCallbackHandle callbackHandle = getMontagePlayer(animMontageKey)
                .playAnimation(animMontage, section);

            // Montage authority is on server side, so if it's played in client side manual networking is required.
            if (!getOwner().isLogicalClient()) {
                NetworkingHelper.getInstance().sendToPlayersTrackingEntityWithSelfIfPlayer(
                    getOwner().getOriginal(),
                    new ClientBoundPlayAnimMontage(animMontageKey.identifier(), section, getOwner().getId())
                );
            }

            // Play root motion if enabled.
            if (animMontage.value().rootMotion() && getOwner().getArmatureComponent() != null) {
                RootMotionComponent rootMotionComponent = getOwner().getArmatureComponent().getRootMotionComponent();
                // Important: used merged clip, the `getPose` method will remove root motion.
                AnimationClip montageClip = animMontage.value().getMergedAnimClip();

                if (rootMotionComponent != null && rootMotionComponent.isEnabled()
                    && montageClip.hasBoneTransform(ArmatureLoader.ROOT_BONE)
                ) {
                    rootMotionComponent.setSource(
                        montageClip.getBoneTransform(ArmatureLoader.ROOT_BONE)::getTransformAt,
                        getMontagePlayer(animMontageKey)::getTime
                    );
                    callbackHandle.addMontageCompletedCallback((_, _, _) -> rootMotionComponent.finishRootMotion());
                }
            }

            return callbackHandle;
        } else {
            Akythera.LOGGER.error("No anim montage found: {}", animMontageKey.identifier());
            return null;
        }
    }

    /// Pause the montage being played immediately
    ///
    /// @param animMontageKey A montage resource key to stop playing. Null if you want to pause any montage
    ///
    /// @return true when paused any of anim montage was being played
    default boolean stopPlayingMontage(@Nullable final ResourceKey<@NonNull AnimationMontage> animMontageKey) {
        // Can't play montage for non-entity object.
        Holder.Reference<@NonNull AnimationMontage> montageHolder = null;

        if (animMontageKey != null) {
            montageHolder = getOwner().getLevel().registryAccess().get(animMontageKey).orElse(null);

            if (montageHolder == null) {
                Akythera.LOGGER.error("No anim montage found: {}", animMontageKey.identifier());
                return false;
            }
        }

        if (animMontageKey == null || getMontagePlayer(animMontageKey).getPlayingMontage() == montageHolder) {
            getMontagePlayer(animMontageKey).terminate(montageHolder);
            return true;
        }

        return false;
    }

    /// Returns a player for [AnimationMontage]
    ///
    /// Montage players are both in client and server, to synchronize playing
    AnimationMontagePlayer getMontagePlayer(final ResourceKey<@NonNull AnimationMontage> animMontageKey);

    @ScriptFunction(pure = true, label = "Soft Pause Animator", desc = """
    Sets the animator as *soft paused*
    A soft paused animator will resume until next animation playing request comes
    """)
    void setSoftPause(final boolean paused);

    @ScriptFunction(pure = true, label = "Hard Pause Animator", desc = """
    Sets the animator as hard paused
    A hard paused animator will resume until hard pause is unlocked explicitly
    """)
    void setHardPause(final boolean paused);

    @ScriptFunction(pure = true, label = "Is Soft Paused", desc = """
    Returns if the animator soft paused
    """)
    boolean isSoftPaused();

    @ScriptFunction(pure = true, label = "Is Hard Paused", desc = """
    Returns if the animator hard paused
    """)
    boolean isHardPaused();

    /// Updates the animator in render tick, reflecting a partial tick
    @ApiStatus.Internal
    void update(final float timeFrame, final float partialTick);

    /// Updates the animator in fixed tick. (0.05)
    @ApiStatus.Internal
    void tick();

    /// Gets the final updated pose by each frame.
    ///
    /// Access poses using [ArmatureComponent#getCachedPose()]
    @ApiStatus.Internal
    Pose getPose();

    /// Returns the owner of this component
    @ScriptFunction(pure = true, label = "Get Owner Entity")
    EntityPatch<Entity> getOwner();

    /// Returns the owner of this component
    @ScriptFunction(pure = true, label = "Get Armature")
    default AssetAccessor<? extends Armature> getArmature() {
        return getOwner().getArmatureComponent().getArmature();
    }
}
