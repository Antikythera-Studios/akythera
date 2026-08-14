package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;
import java.util.function.Supplier;

/// Module for animation based movements for the objects that
/// implements [AnimatableObject].
///
/// No need to call in developer side, this module is meant to run under API.
@ApiStatus.Internal
public final class RootMotionComponent {
    private final EntityPatch<Entity> owner;
    private final LogicalSide rootMotionUpdateSide;

    /// Root motion transform getter
    private Function<Float, Transform> rootMotionTransformProvider;
    private Supplier<Float> playbackTimeSupplier;

    private boolean enabled = true;

    public RootMotionComponent(final EntityPatch<Entity> owner) {
        this.owner = owner;

        // Decide the logical side of root motion updates, player positions are sent from client -> server,
        // but AI controlled entities are sent from server -> client.
        rootMotionUpdateSide = owner.isControlledByPlayer() ? LogicalSide.CLIENT : LogicalSide.SERVER;
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
    /// The real movement input is entered depending on the [#owner],
    /// the player-controlling entities are in client, otherwise it's
    /// in server
    public void tick() {
        if (!enabled) {
            return;
        }

        // No root motion is being played
        if (rootMotionTransformProvider == null || playbackTimeSupplier == null) return;

        // update only in logical servers. Player controlled entities are fine to update only in `frameUpdate`
        // since their position will automatically be sent to server by Minecraft's update packet.
        if (owner.isLogicalServer() && rootMotionUpdateSide.isEntityOnValidSide(owner.getOriginal())) {
            deltaMoveEntity(0.05F);
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
        if (owner.isLogicalClient()) {
            deltaMoveEntity(deltaTime);
        }
    }

    private void deltaMoveEntity(final float deltaTime) {
        float currentTime = playbackTimeSupplier.get();
        float previousTime = Math.max(currentTime - deltaTime, 0);
        Transform previousTransform = rootMotionTransformProvider.apply(previousTime);
        Transform currentTransform = rootMotionTransformProvider.apply(currentTime);

        // Subtraction is inversed since Minecraft's coord system uses positive Z as forward.
        Vec3 rootDelta = previousTransform.loc().subtract(currentTransform.loc());

        // TODO: may open a way to choose applying pitch rotation.
        Vec3 worldDelta = owner.fromRelativeToWorldVector(rootDelta, false);
        owner.getOriginal().move(MoverType.SELF, worldDelta);
    }
}
