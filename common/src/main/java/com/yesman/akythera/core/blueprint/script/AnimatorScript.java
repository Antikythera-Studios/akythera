package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.animation.animator.AnimationLayer;
import com.yesman.akythera.api.animation.animator.AnimationSequenceInterface;
import com.yesman.akythera.api.animation.animator.BlendSpace2DInterface;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.driver.AnimationPlayer;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.animation.playable.BlendSpace2D;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/// A set of [BlendingAnimator] control logics, modularizing each
/// task when the events are triggered
public interface AnimatorScript {
    /// Helper function to return [AnimationDriver] from currently loaded [RegistryAccess]
    static AnimationDriver instantiateAnimSequence(final ResourceKey<@NonNull AnimationSequence> resourceKey)
        throws NoSuchElementException
    {
        return AnimationPlayer.createInstance(StaticRegistryAccess.getRegistryAccess().getOrThrow(resourceKey));
    }

    /// Helper function to return [AnimationDriver] from currently loaded [RegistryAccess]
    ///
    /// @param xUpdater Wire X axis value provider
    /// @param yUpdater Wire Y axis value provider
    static AnimationDriver instantiateBlendSpace2D(
        final ResourceKey<@NonNull BlendSpace2D> resourceKey, final Supplier<Float> xUpdater, final Supplier<Float> yUpdater
    ) throws NoSuchElementException
    {
        return BlendSpace2D.instantiate(StaticRegistryAccess.getRegistryAccess().getOrThrow(resourceKey), xUpdater, yUpdater);
    }

    /// Instantiate animation interface for sequence
    static AnimationDriver instantiateAnimSequenceInterface(
        final BlendingAnimator animator, final AnimationInterfaceKey interfaceKey, final ResourceKey<@NonNull AnimationSequence> resourceKey
    ) {
        var animSequenceInterface = new AnimationSequenceInterface(StaticRegistryAccess.getRegistryAccess().getOrThrow(resourceKey));
        animSequenceInterface.reset(); // Lazy init the default driver
        animator.addAnimInterface(interfaceKey, animSequenceInterface);

        return animSequenceInterface;
    }

    /// Instantiate animation interface for blend space (2D)
    static AnimationDriver instantiateBlendSpace2DInterface(
        final BlendingAnimator animator, final AnimationInterfaceKey interfaceKey,
        final ResourceKey<@NonNull BlendSpace2D> resourceKey, final Supplier<Float> xUpdater, final Supplier<Float> yUpdater
    ) {
        var blendSpace2DInterface = new BlendSpace2DInterface(StaticRegistryAccess.getRegistryAccess().getOrThrow(resourceKey), xUpdater, yUpdater);
        blendSpace2DInterface.reset(); // Lazy init the default driver
        animator.addAnimInterface(interfaceKey, blendSpace2DInterface);

        return blendSpace2DInterface;
    }

    /// Called on animator being constructed. Build animation logics for the owner's locomotion
    ///
    /// It is ideal to construct animator with infinitely looping state machine for a base layer
    /// then mix it with other layers for actions like bow aiming, blocking, etc.
    ///
    /// @param animLayerMap      Use it for building animation layers. Each layer contain montage player.
    /// @param cachedPoseDrivers Use it for building pose drivers which is references multiple times through multiple layers in one frame.
    void construct(final Map<AnimationLayerSignature, AnimationLayer> animLayerMap, final Map<String, AnimationDriver> cachedPoseDrivers, final BlendingAnimator animator);

    /// Called on determining the final pose of animator. Build logics for combining each layer.
    Pose getFinalPose(final Map<AnimationLayerSignature, AnimationLayer> layers);

    /// Called on each frame update.
    void update(final float frameTime, final float partialTick);
}
