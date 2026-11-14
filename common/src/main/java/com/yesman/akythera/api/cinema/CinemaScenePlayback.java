package com.yesman.akythera.api.cinema;

import com.yesman.akythera.api.component.impl.MovementComponent;
import com.yesman.akythera.api.component.impl.RootMotionComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import net.minecraft.SharedConstants;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/// One actor's part in a playing [CinemaScene], kept by its [EntityPatch] until the scene ends. It anchors the root
/// motion the actor plays to the actor's place around [#root], or holds an actor that plays none at that place.
public final class CinemaScenePlayback implements RootMotionComponent.Anchor {
    private final CinemaScene scene;
    private final CinemaScene.Actor actor;
    private final CinemaSceneRoot root;

    /// Ticks the scene ran.
    private int ticks;

    /// Whether [#tick] holds the actor at [#placement], for an actor whose root motion does not follow the scene.
    private boolean holdsActor;

    public CinemaScenePlayback(final CinemaScene scene, final CinemaScene.Actor actor, final CinemaSceneRoot root) {
        this.scene = scene;
        this.actor = actor;
        this.root = root;
    }

    public CinemaScene getScene() {
        return scene;
    }

    public CinemaScene.Actor getActor() {
        return actor;
    }

    public CinemaSceneRoot getRoot() {
        return root;
    }

    public void setHoldsActor(final boolean holdsActor) {
        this.holdsActor = holdsActor;
    }

    /// Counts a tick and holds `patch` at its place on the side that moves it, answering whether the scene ended.
    public boolean tick(final EntityPatch<?> patch) {
        ticks++;

        MovementComponent movement = patch.findComponent(MovementComponent.class);

        if (holdsActor && movement != null && patch.getOriginal().isLocalInstanceAuthoritative()) {
            Transform placed = placement();
            float yRot = 180.0F - (float)Math.toDegrees(QuaternionUtils.toSwingTwist(placed.rot()).y);

            if (actor.ignoreBlockCollision()) {
                movement.moveTo(placed.loc());
            } else {
                movement.move(placed.loc().subtract(movement.position()));
            }

            movement.turn(Mth.wrapDegrees(yRot - movement.yRot()));
        }

        return time(0.0F) >= scene.duration();
    }

    /// Seconds the scene ran, `partialTick` after the last tick.
    public float time(final float partialTick) {
        return (ticks + partialTick) / SharedConstants.TICKS_PER_SECOND;
    }

    /// Where the scene places `patch`'s camera `partialTick` after the last tick, in world space. Null for an actor with
    /// no camera keys.
    public @Nullable Transform cameraTransform(final EntityPatch<?> patch, final float partialTick) {
        CinemaScene.Camera camera = actor.camera();

        if (camera.keys().isEmpty()) return null;

        Transform space = camera.followsActor() ? patch.getModelTransform(partialTick) : root.transform(partialTick);
        Transform placed = space.mul(camera.keys().sample(time(partialTick), Transform.IDENTITY, Transform::slerp));

        return new Transform(placed.loc(), placed.rot(), Transform.IDENTITY.sca());
    }

    // ***************************************************************
    // RootMotionComponent.Anchor
    // ***************************************************************

    @Override
    public Transform placement() {
        return root.transform(1.0F).mul(actor.transform());
    }

    @Override
    public boolean ignoresBlockCollision() {
        return actor.ignoreBlockCollision();
    }
}
