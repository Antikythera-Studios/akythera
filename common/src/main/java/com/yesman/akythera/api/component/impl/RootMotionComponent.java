package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.animation.notify.impl.MotionWarpingNotifyState;
import com.yesman.akythera.api.animation.warping.MotionWarpTarget;
import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TickableDynamicComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.RootMotionComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/// Module for animation based movements for the objects that implements [AnimatableObject]. Attached under the
/// [ArmatureComponent] whose montages it follows, and moves that armature's owner through the owner's
/// [MovementComponent], moving nothing while the owner has none. The root's yaw turns the owner, and its location
/// moves the owner in the root's own facing.
///
/// Built by [RootMotionComponentBlueprint]. The armature ticks it.
public final class RootMotionComponent implements UniqueComponent, TickableDynamicComponent<ArmatureComponent> {
    /// The armature this module is attached under, whose owner it moves.
    private ArmatureComponent armature;

    /// Root motion transform getter
    private Function<Float, Transform> rootMotionTransformProvider;
    private Supplier<Float> playbackTimeSupplier;

    /// Playback speed multiplier of the source, converting the frame time to the playback time it advanced.
    private float playRate = 1.0F;

    /// Whether the root's vertical movement moves the owner.
    private boolean moveVertical = true;

    /// The root's yaw where the source started playing, in radians.
    private double startYaw;

    private boolean enabled = true;

    /// The motion warping window the playback is in, or null.
    private @Nullable Warp warp;

    public RootMotionComponent(final ArmatureComponent armature) {
        this.armature = armature;
    }

    /// Called on animation with root motion enabled
    public void setSource(
        final Function<Float, Transform> rootMotionTransformProvider, final Supplier<Float> playbackTimeSupplier,
        final float playRate, final boolean moveVertical
    ) {
        this.rootMotionTransformProvider = rootMotionTransformProvider;
        this.playbackTimeSupplier = playbackTimeSupplier;
        this.playRate = playRate;
        this.moveVertical = moveVertical;
        this.startYaw = yawOf(rootMotionTransformProvider.apply(playbackTimeSupplier.get()));
    }

    public void finishRootMotion() {
        rootMotionTransformProvider = null;
        playbackTimeSupplier = null;
        warp = null;
    }

    /// Whether a root motion source is being played.
    public boolean isPlaying() {
        return rootMotionTransformProvider != null && playbackTimeSupplier != null;
    }

    /// Starts warping the root motion by `settings` until the playback reaches `endTime`, replacing any warp.
    public Warp startWarp(final MotionWarpingNotifyState settings, final MotionWarpTarget target, final float endTime) {
        warp = new Warp(settings, target, endTime);
        return warp;
    }

    public @Nullable Warp getWarp() {
        return warp;
    }

    /// Stops the warp `settings` started, leaving another one running.
    public void stopWarp(final MotionWarpingNotifyState settings) {
        if (warp != null && warp.settings == settings) warp = null;
    }

    private void deltaMove(final float deltaTime) {
        MovementComponent movement = armature.getOwner().findComponent(MovementComponent.class);

        if (movement == null) return;

        float currentTime = playbackTimeSupplier.get();
        float previousTime = Math.max(currentTime - deltaTime * playRate, 0);
        Transform previousTransform = rootMotionTransformProvider.apply(previousTime);
        Transform currentTransform = rootMotionTransformProvider.apply(currentTime);
        Vec3 rootDelta = facingDelta(previousTransform, currentTransform);

        if (warp != null) rootDelta = rootDelta.multiply(warp.settings.modifyX() ? warp.stretch : 1.0F, warp.verticalStretch, warp.stretch);

        // TODO: may open a way to choose applying pitch rotation.
        Vec3 worldDelta = movement.toWorldVector(rootDelta);

        movement.move(moveVertical ? worldDelta : worldDelta.multiply(1.0D, 0.0D, 1.0D));

        if (warp == null || !warp.turns()) {
            // A turn around +Y is a negative Minecraft yaw
            movement.turn(-Mth.wrapDegrees((float)Math.toDegrees(yawOf(currentTransform) - yawOf(previousTransform))));
        }
    }

    /// The root's movement from `from` to `to` in the owner's facing space (+Z forward), seen from the root's facing
    /// at `from`.
    private Vec3 facingDelta(final Transform from, final Transform to) {
        Quaterniond unturn = new Quaterniond().rotationY(startYaw - yawOf(from));

        // Subtraction is inversed since Minecraft's coord system uses positive Z as forward. The armature's own
        // transform turns and scales it into the model space.
        return armature.modelTransform().mulDirection(VectorUtils.rotateByQuaternion(unturn, from.loc().subtract(to.loc())));
    }

    /// The twist of `root` around Y, in radians.
    private static double yawOf(final Transform root) {
        return QuaternionUtils.toSwingTwist(root.rot()).y;
    }

    // ***************************************************************
    // TickableDynamicComponent
    // ***************************************************************

    /// Called in fixed tick(0.05). Called both in server and client
    ///
    /// The real movement input is entered depending on the owner,
    /// the player-controlling entities are in client, otherwise it's
    /// in server.
    @Override
    public void tick() {
        // No root motion is being played
        if (!isPlaying()) return;

        // update only in logical servers. Player controlled entities are fine to update only in `frameUpdate`
        // since their position will automatically be sent to server by Minecraft's update packet.
        ComponentAttachable owner = armature.getOwner();

        if (owner.logicalSide() == LogicalSide.SERVER && !(owner instanceof EntityPatch<?> entityPatch && entityPatch.isControlledByPlayer())) {
            deltaMove(0.05F);
        }
    }

    /// Called only in render tick in client. Updates the visual position.
    @ClientOnly @Override
    public void frameUpdate(final float frameTime, final float partialTick) {
        // No root motion is being played
        if (!isPlaying()) return;

        // frame update is done in client side, for both players and AI entities.
        // this will prevent the root motion looks like `sliding` by custom interval
        // interpolations implemented by Minecraft.
        if (armature.getOwner().logicalSide().validInClientSide()) {
            deltaMove(frameTime);
        }
    }

    @Override
    public ArmatureComponent getOwner() {
        return armature;
    }

    @Override
    public RootMotionComponent getRenewed(final ArmatureComponent newOwner) {
        armature = newOwner;
        return this;
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /// One motion warping window: where it warps to, and how far it stretches the root's movement.
    public final class Warp {
        private final MotionWarpingNotifyState settings;

        /// Built for this window's owner.
        private final MotionWarpTarget target;

        /// Where the window ends, in the playback time.
        private final float endTime;

        /// Ticks the window ran.
        private int ticks;

        /// The destination the last update found, in world space. Null for none.
        private @Nullable Vec3 destination;

        /// Multiplier of the root's forward (Z) movement, and of its strafing (X) movement with
        /// [MotionWarpingNotifyState#modifyX].
        private float stretch = 1.0F;

        /// Multiplier of the root's vertical (Y) movement.
        private float verticalStretch = 1.0F;

        private Warp(final MotionWarpingNotifyState settings, final MotionWarpTarget target, final float endTime) {
            this.settings = settings;
            this.target = target;
            this.endTime = endTime;
        }

        public MotionWarpingNotifyState settings() {
            return settings;
        }

        public MotionWarpTarget target() {
            return target;
        }

        public @Nullable Vec3 destination() {
            return destination;
        }

        /// Whether the warp turns the owner in place of the root's yaw.
        public boolean turns() {
            return settings.warpRotation() && destination != null;
        }

        /// Counts a tick, answering whether this one updates the warp. The first tick always does.
        public boolean tickUpdate() {
            return ticks++ % Math.max(settings.updateRate(), 1) == 0;
        }

        /// Aims the stretch so the root's remaining movement in the window lands on `destination`, from `position`,
        /// each in world space. A null destination stops stretching.
        public void update(final @Nullable Vec3 destination, final Vec3 position) {
            this.destination = destination;

            if (destination == null || !settings.warpTranslation()) {
                stretch = 1.0F;
                verticalStretch = 1.0F;
                return;
            }

            MovementComponent movement = armature.getOwner().findComponent(MovementComponent.class);

            if (!isPlaying() || movement == null) return;

            float currentTime = playbackTimeSupplier.get();

            if (currentTime >= endTime) return;

            Vec3 remaining = facingDelta(rootMotionTransformProvider.apply(currentTime), rootMotionTransformProvider.apply(endTime));
            Vec3 gap = destination.subtract(position);
            float maxChange = settings.smooth() <= 0.0F ? Float.MAX_VALUE : settings.smooth();

            // With strafing, the whole horizontal path scales to the distance. Otherwise only the forward movement
            // reaches the destination's distance ahead.
            double reach = settings.modifyX() ? remaining.horizontalDistance() : remaining.z;
            double gapReach = settings.modifyX() ? gap.horizontalDistance() : gap.dot(movement.toWorldVector(MathUtils.ZP));

            if (Math.abs(reach) > 1.0E-4D) {
                float goal = Math.clamp((float)(gapReach / reach), 0.0F, settings.maxStretch());
                stretch = Mth.approach(stretch, goal, maxChange);
            }

            if (settings.modifyY() && Math.abs(remaining.y) > 1.0E-4D) {
                float goal = Math.clamp((float)(gap.y / remaining.y), 0.0F, settings.maxStretch());
                verticalStretch = Mth.approach(verticalStretch, goal, maxChange);
            }
        }
    }
}
