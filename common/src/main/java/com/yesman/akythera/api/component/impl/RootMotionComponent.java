package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;
import java.util.function.Supplier;

/// Module for animation based movements for the objects that
/// implements [AnimatableObject]. Moves the owner of [#armature] through the owner's
/// [MovementComponent], and moves nothing while the owner has none.
///
/// No need to call in developer side, this module is meant to run under API.
@ApiStatus.Internal
public final class RootMotionComponent {
    /// The armature this module belongs to, whose owner it moves.
    private final ArmatureComponent armature;

    /// Root motion transform getter
    private Function<Float, Transform> rootMotionTransformProvider;
    private Supplier<Float> playbackTimeSupplier;

    private boolean enabled = true;

    public RootMotionComponent(final ArmatureComponent armature) {
        this.armature = armature;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /// Called on animation with root motion enabled
    public void setSource(final Function<Float, Transform> rootMotionTransformProvider, final Supplier<Float> playbackTimeSupplier) {
        this.rootMotionTransformProvider = rootMotionTransformProvider;
        this.playbackTimeSupplier = playbackTimeSupplier;
    }

    public void finishRootMotion() {
        rootMotionTransformProvider = null;
        playbackTimeSupplier = null;
    }

    /// Called in fixed tick(0.05). Called both in server and client
    ///
    /// The real movement input is entered depending on the owner,
    /// the player-controlling entities are in client, otherwise it's
    /// in server.
    public void tick() {
        if (!enabled) {
            return;
        }

        // No root motion is being played
        if (rootMotionTransformProvider == null || playbackTimeSupplier == null) return;

        // update only in logical servers. Player controlled entities are fine to update only in `frameUpdate`
        // since their position will automatically be sent to server by Minecraft's update packet.
        ComponentAttachable owner = armature.getOwner();

        if (owner.logicalSide() == LogicalSide.SERVER && !(owner instanceof EntityPatch<?> entityPatch && entityPatch.isControlledByPlayer())) {
            deltaMove(0.05F);
        }
    }

    /// Called only in render tick in client. Updates the visual position.
    @ClientOnly
    public void frameUpdate(final float deltaTime) {
        if (!enabled) {
            return;
        }

        // No root motion is being played
        if (rootMotionTransformProvider == null || playbackTimeSupplier == null) return;

        // frame update is done in client side, for both players and AI entities.
        // this will prevent the root motion looks like `sliding` by custom interval
        // interpolations implemented by Minecraft.
        if (armature.getOwner().logicalSide().validInClientSide()) {
            deltaMove(deltaTime);
        }
    }

    private void deltaMove(final float deltaTime) {
        MovementComponent movement = armature.getOwner().findComponent(MovementComponent.class);

        if (movement == null) return;

        float currentTime = playbackTimeSupplier.get();
        float previousTime = Math.max(currentTime - deltaTime, 0);
        Transform previousTransform = rootMotionTransformProvider.apply(previousTime);
        Transform currentTransform = rootMotionTransformProvider.apply(currentTime);

        // Subtraction is inversed since Minecraft's coord system uses positive Z as forward. The armature's own
        // transform turns and scales it into the model space.
        Vec3 rootDelta = armature.modelTransform().mulDirection(previousTransform.loc().subtract(currentTransform.loc()));

        // TODO: may open a way to choose applying pitch rotation.
        movement.move(movement.toWorldVector(rootDelta));
    }
}
