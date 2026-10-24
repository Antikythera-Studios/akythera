package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.physics.ArmaturePhysicsManager;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TickableComponent;
import com.yesman.akythera.api.component.TickableDynamicComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundRagdollImpulse;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundRagdollVelocity;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundStartRagdoll;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundStopRagdoll;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/// Module for skeletal mesh armature. Animator and armature are paired since both are coupled to work.
///
/// Implements [ComponentAttachable]. A [TransformSocket] attached here binds to a bone; anything
/// else attached here stands in for the entity's own model, which is how a skeletal mesh replaces
/// the vanilla one.
///
/// A patch may hold several, one per animated layer. [EntityPatch#getArmatureComponent] answers the
/// first one it lists.
///
/// The owner is any [ComponentAttachable], and the armature lives on the owner's
/// [ComponentAttachable#logicalSide].
///
/// Its [TransformComponent#localTransform] places the whole skeleton: bones, sockets and meshes all live in its space.
/// [#getBoneModelTransform] stays in the armature's own space.
@ScriptType
public class ArmatureComponent
    implements DynamicPatchComponent<ComponentAttachable>, TickableDynamicComponent<ComponentAttachable>, ComponentAttachable, TransformComponent
{
    /// One server tick, in seconds. The client passes real frame time instead.
    private static final float SERVER_FRAME_TIME = 0.05F;

    private final AssetAccessor<? extends Armature> armature;

    /// Null only on a preview armature that has no animator installed yet, which draws the bind pose.
    private final @Nullable Animator animator;

    /// The attached components by name, in declaration order—[TransformSocket]s and whatever draws
    /// the entity's body, and a [RootMotionComponent].
    private final Map<String, PatchComponent> components = new LinkedHashMap<>();

    /// [#components] indexed by [UniqueComponent] type, built once the children are.
    private final Map<Class<? extends UniqueComponent>, ? extends UniqueComponent> uniqueComponents;

    private ComponentAttachable owner;

    /// Where the skeleton sits in [#owner]'s space.
    private Transform localTransform;

    /// The elements of [#owner]'s space the skeleton takes on.
    private final TransformInheritPolicy transformInheritPolicy;

    /// Last pose resulted by [Animator#getPose()]
    ///
    /// Access pose via this field instead of calling [Animator#getPose()] every time.
    private Pose cachedPose;

    /// The forward-bound skeleton at the pose [#updateBoundTransforms] last wrote. Sized on the
    /// first update: a preview armature has no armature yet when its constructor runs.
    private Matrix4f @Nullable [] boundTransforms;

    /// The skeleton [#boundTransforms] held before the last server pose, swapped with it rather than
    /// copied. Null on a client, which poses per frame and never needs a second one.
    private Matrix4f @Nullable [] previousBoundTransforms;

    /// Game tick [#boundTransforms] was built for, and the tick [#previousBoundTransforms] was built
    /// for. Server-side only; both stay [Long#MIN_VALUE] on a client.
    private long poseTick = Long.MIN_VALUE;
    private long previousPoseTick = Long.MIN_VALUE;

    /// Whether this armature takes part, see [PatchComponent#isEnabled].
    private boolean enabled = true;

    /// Whether [#frameUpdate] ran since the last [#tick]. A client has no frame for an entity it doesn't draw.
    private boolean frameUpdatedSinceTick;

    /// The rag doll drawn in place of the animation, null while the animation is drawn. Client-side only.
    private @Nullable RagdollSimulation ragdoll;

    /// The animation as the last frame drew it, as the last frame before the running tick drew it, and as the last
    /// frame before the tick ahead of that drew it. Client-side only, and only while no rag doll runs.
    private RagdollSimulation.@Nullable Snapshot frameSnapshot;
    private RagdollSimulation.@Nullable Snapshot tickSnapshot;
    private RagdollSimulation.@Nullable Snapshot previousTickSnapshot;

    /// Ticks this armature has run on a client.
    private long clientTicks;

    /// The partial tick of the last [#frameUpdate].
    private float lastPartialTick = 1.0F;

    /// Ticks the rag doll has left to simulate, 0 while none runs. Kept on both sides: the server reads it for the
    /// interactions it switches off and the death it holds back.
    private int ragdollTicksLeft;

    public ArmatureComponent(
        final ComponentAttachable owner,
        final AssetAccessor<? extends Armature> armature,
        final Animator animator
    ) {
        this(owner, armature, animator, Transform.IDENTITY, TransformInheritPolicy.LOC_ROT_SCA, Map.of());
    }

    public ArmatureComponent(
        final ComponentAttachable owner,
        final AssetAccessor<? extends Armature> armature,
        final @Nullable Animator animator,
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final Map<String, ComponentBlueprint<?, ?>> childBlueprints
    ) {
        this.armature = armature;
        this.animator = animator;
        this.owner = owner;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;

        if (animator != null) animator.setArmatureComponent(this);

        ComponentBlueprint.instantiateAll(childBlueprints, this, owner.logicalSide().validInClientSide(), components::put);

        uniqueComponents = ComponentAttachable.indexUnique(components);
    }

    /// The armature that poses what `host` places: the host itself, the one a socket rides, or a patch's first.
    public static @Nullable ArmatureComponent armatureOf(final ComponentAttachable host) {
        return switch (host) {
            case ArmatureComponent armature -> armature;
            case TransformSocket socket -> socket.getArmature();
            case EntityPatch<?> patch -> patch.getArmatureComponent();
            default -> null;
        };
    }

    @Override
    public void tick() {
        // The client advances animation time in [#frameUpdate], once per frame. A server has no frame,
        // and neither has a client entity nothing drew this tick (the first-person player), so the tick
        // does it—before [Animator#tick], whose notify window is [lastUpdatedTime, elapsedTime] and so
        // has to see the time this tick moved to.
        //
        // Time only. The skeleton itself is built on demand, in [#ensureServerPose].
        if (owner.logicalSide() == LogicalSide.SERVER) {
            getAnimator().update(SERVER_FRAME_TIME, 1.0F);
        } else if (!frameUpdatedSinceTick) {
            getAnimator().update(SERVER_FRAME_TIME, 1.0F);

            components.values().forEach(component -> {
                if (component.isEnabled() && component instanceof TickableDynamicComponent<?> dynamic) dynamic.frameUpdate(SERVER_FRAME_TIME, 1.0F);
            });
        }

        frameUpdatedSinceTick = false;

        if (owner.logicalSide() != LogicalSide.SERVER) {
            clientTicks++;
            previousTickSnapshot = tickSnapshot;
            tickSnapshot = frameSnapshot;

            if (ragdoll != null) {
                ragdoll.tick();

                Entity entity = ragdollEntity();

                if (entity != null) entity.setPos(ragdoll.anchorPosition().subtract(0.0D, entity.getBbHeight() * 0.5D, 0.0D));
            }
        }

        if (ragdollTicksLeft > 0 && --ragdollTicksLeft == 0) endRagdollTime();

        getAnimator().tick();

        components.values().forEach(component -> {
            if (component.isEnabled() && component instanceof TickableComponent tickable) tickable.tick();
        });
    }

    @Override
    public void frameUpdate(float frameTime, float partialTick) {
        frameUpdatedSinceTick = true;
        lastPartialTick = partialTick;
        getAnimator().update(frameTime, partialTick);
        updateBoundTransforms();

        components.values().forEach(component -> {
            if (component.isEnabled() && component instanceof TickableDynamicComponent<?> dynamic) dynamic.frameUpdate(frameTime, partialTick);
        });
    }

    /// Rewrites [#boundTransforms] from the rag doll while one runs, or from the animator's current pose.
    public void updateBoundTransforms() {
        AssetAccessor<? extends Armature> accessor = getArmature();
        Armature resolved = accessor == null ? null : accessor.get();

        if (resolved == null) return;

        if (boundTransforms == null || boundTransforms.length != resolved.getBoneCount()) {
            boundTransforms = Matrix4fUtils.allocateMatrixArray(resolved.getBoneCount());
        }

        Pose pose;

        if (ragdoll != null) {
            pose = ragdoll.makePose(armatureToWorld(lastPartialTick), lastPartialTick);
        } else {
            pose = getAnimator().getPose();

            if (owner.logicalSide() != LogicalSide.SERVER) {
                frameSnapshot = new RagdollSimulation.Snapshot(pose, armatureToWorld(lastPartialTick), (clientTicks + lastPartialTick) * SERVER_FRAME_TIME);
            }
        }

        ArmaturePhysicsData physics = ArmaturePhysicsManager.ofArmature(accessor);
        cachedPose = physics == null ? pose : resolved.applyBoneConstraints(pose, physics);
        cachedPose.getForwardBoundTransforms(accessor, boundTransforms);
    }

    /// Lets the bodies of this armature's [ArmaturePhysicsData] fall as a rag doll for `durationTicks`, drawn in place
    /// of the animation. The bodies start moving as the animation moved over the last tick, times `velocityScale`.
    ///
    /// The client simulates. While it runs, the client places the entity on the rag doll and ignores the positions
    /// the server sends; on both sides the entity's AI, movement and collision are off, and a dead entity is not removed.
    /// When the time is up, a dead entity's bodies freeze until vanilla removes it, and a living one is drawn by its
    /// animation again.
    ///
    /// Called on a server, the rag doll is sent to every client tracking the entity, when this is the entity's first
    /// armature. False on a client when the armature has no body to simulate.
    @ScriptFunction(label = "Start Ragdoll", desc = """
        Lets the armature's bodies fall as a rag doll for the duration in ticks, moving on as the animation moved over the last tick times the velocity scale. Called on a server, every tracking client runs it
    """)
    public boolean startRagdoll(final float velocityScale, final int durationTicks) {
        int duration = Math.max(1, durationTicks);

        if (owner.logicalSide() == LogicalSide.SERVER) replicateRagdoll(entityId -> new ClientBoundStartRagdoll(entityId, velocityScale, duration));

        return startLocalRagdoll(velocityScale, duration);
    }

    /// [#startRagdoll] on this side alone: a server only keeps the time, which switches the entity's interactions off
    /// and holds its death back, and a client simulates. For code that runs on both sides by itself, such as an
    /// animation notify, so nothing is sent.
    public boolean startLocalRagdoll(final float velocityScale, final int durationTicks) {
        int duration = Math.max(1, durationTicks);

        if (owner.logicalSide() == LogicalSide.SERVER) {
            ragdollTicksLeft = duration;

            EntityPatch<?> patch = TransformComponent.entityOf(owner);

            if (patch != null) patch.getOriginal().setDeltaMovement(Vec3.ZERO);

            return true;
        }

        AssetAccessor<? extends Armature> accessor = getArmature();
        Armature resolved = accessor == null ? null : accessor.get();
        ArmaturePhysicsData physics = accessor == null ? null : ArmaturePhysicsManager.ofArmature(accessor);

        if (resolved == null || physics == null) return false;

        RagdollSimulation.Snapshot current = frameSnapshot != null
            ? frameSnapshot
            : new RagdollSimulation.Snapshot(getAnimator().getPose(), armatureToWorld(1.0F), clientTicks * SERVER_FRAME_TIME);
        RagdollSimulation.Snapshot previous = tickSnapshot != current ? tickSnapshot : previousTickSnapshot;
        EntityPatch<?> entity = TransformComponent.entityOf(owner);

        ragdoll = RagdollSimulation.start(resolved, physics, entity == null ? null : RagdollSimulation.Terrain.of(entity.getLevel()), previous, current, velocityScale);

        if (ragdoll != null) ragdollTicksLeft = duration;

        return ragdoll != null;
    }

    /// Stops the rag doll and draws the animation again, with the entity back at the position the server last sent.
    /// Called on a server, every tracking client stops it too.
    @ScriptFunction(label = "Stop Ragdoll", desc = """
        Stops the rag doll and draws the animation again, with the entity back where the server holds it. Called on a server, every tracking client stops it
    """)
    public void stopRagdoll() {
        if (owner.logicalSide() == LogicalSide.SERVER) {
            if (ragdollTicksLeft > 0) replicateRagdoll(ClientBoundStopRagdoll::new);

            ragdollTicksLeft = 0;
            return;
        }

        boolean wasRunning = ragdoll != null;
        clearRagdoll();

        Entity entity = ragdollEntity();

        if (wasRunning && entity != null) entity.snapTo(entity.getPositionCodec().decode(0L, 0L, 0L));
    }

    /// Whether a rag doll runs: on a server, while its time lasts; on a client, while its bodies are drawn, which for
    /// a dead entity is until the entity is removed.
    @ScriptFunction(pure = true, label = "Is Ragdoll Running")
    public boolean isRagdollRunning() {
        return ragdollTicksLeft > 0 || ragdoll != null;
    }

    /// Ticks the rag doll has left to simulate, 0 while none runs.
    public int getRagdollTicksLeft() {
        return ragdollTicksLeft;
    }

    /// Pushes the rag doll's body of `boneName` by `impulse` at `worldPoint`, null for its center of mass. The joints
    /// carry the push on to the other bodies. Called on a server, it is sent to every tracking client. Does nothing
    /// while no rag doll runs.
    @ScriptFunction(label = "Apply Ragdoll Impulse", desc = """
        Pushes the rag doll's body of the bone at a world point, null for its center of mass. The joints carry the push on to the other bodies. Called on a server, every tracking client applies it
    """)
    public void applyRagdollImpulse(final String boneName, final Vec3 impulse, final @Nullable Vec3 worldPoint) {
        if (owner.logicalSide() == LogicalSide.SERVER) {
            if (ragdollTicksLeft > 0) replicateRagdoll(entityId -> new ClientBoundRagdollImpulse(entityId, boneName, impulse, Optional.ofNullable(worldPoint)));
        } else if (ragdoll != null) {
            ragdoll.applyImpulse(boneName, impulse, worldPoint);
        }
    }

    /// Adds `velocity` to every body of the rag doll alike, in blocks per second. Called on a server, it is sent to
    /// every tracking client. Does nothing while no rag doll runs.
    @ScriptFunction(label = "Add Ragdoll Velocity", desc = """
        Adds a velocity, in blocks per second, to every body of the rag doll alike. Called on a server, every tracking client adds it
    """)
    public void addRagdollVelocity(final Vec3 velocity) {
        if (owner.logicalSide() == LogicalSide.SERVER) {
            if (ragdollTicksLeft > 0) replicateRagdoll(entityId -> new ClientBoundRagdollVelocity(entityId, velocity));
        } else if (ragdoll != null) {
            ragdoll.addVelocity(velocity);
        }
    }

    /// The rag doll's time is up. A client freezes a dead entity's bodies until vanilla removes it, or draws a living one
    /// by its animation again.
    private void endRagdollTime() {
        if (owner.logicalSide() == LogicalSide.SERVER || ragdoll == null) return;

        EntityPatch<?> patch = TransformComponent.entityOf(owner);

        if (patch != null && patch.getOriginal() instanceof LivingEntity living && living.isDeadOrDying()) {
            ragdoll.rest();
        } else {
            stopRagdoll();
        }
    }

    /// Sends `payload` for this armature's entity to every client tracking it, when this is the entity's first
    /// armature, the one the payloads address.
    private void replicateRagdoll(final IntFunction<CustomPacketPayload> payload) {
        EntityPatch<?> patch = TransformComponent.entityOf(owner);

        if (patch == null || patch.getArmatureComponent() != this) return;

        NetworkingHelper.getInstance().sendToPlayersTrackingEntityWithSelfIfPlayer(patch.getOriginal(), payload.apply(patch.getId()));
    }

    private void clearRagdoll() {
        ragdoll = null;
        ragdollTicksLeft = 0;
        frameSnapshot = null;
        tickSnapshot = null;
        previousTickSnapshot = null;
    }

    /// The entity a running rag doll places on the client, null when the armature belongs to none or the client
    /// moves it itself (the local player).
    private @Nullable Entity ragdollEntity() {
        EntityPatch<?> patch = owner.logicalSide() == LogicalSide.SERVER ? null : TransformComponent.entityOf(owner);

        return patch == null || patch.getOriginal().isLocalInstanceAuthoritative() ? null : patch.getOriginal();
    }

    /// Where the armature stands in the world `partialTick` into the tick, or in its owner's space when it belongs to
    /// no entity.
    private Transform armatureToWorld(final float partialTick) {
        Transform world = worldTransform(partialTick);

        return world == null ? modelTransform() : world;
    }

    /// Builds this armature's skeleton for the current server tick, unless this tick already built it.
    /// Does nothing on a client, where [#frameUpdate] poses every frame for rendering.
    ///
    /// Server posing is on demand because most entities are never asked: the cost lands only on the
    /// ones a collision query actually reads, on the ticks it reads them.
    private void ensureServerPose() {
        if (owner.logicalSide() != LogicalSide.SERVER || !(owner instanceof EntityPatch<?> entityPatch)) return;

        long tick = entityPatch.getLevel().getGameTime();

        if (tick == poseTick) return;

        previousPoseTick = poseTick;
        poseTick = tick;

        // The array being retired becomes the scratch the new pose is written into. Null on the first
        // pose, which [#updateBoundTransforms] allocates for.
        Matrix4f[] retired = previousBoundTransforms;
        previousBoundTransforms = boundTransforms;
        boundTransforms = retired;

        updateBoundTransforms();
    }

    public Animator getAnimator() {
        return animator;
    }

    public AssetAccessor<? extends Armature> getArmature() {
        return armature;
    }

    public Pose getCachedPose() {
        return cachedPose;
    }

    public Matrix4f[] getBoundTransforms() {
        return boundTransforms;
    }

    public Matrix4f @Nullable [] getPreviousBoundTransforms() {
        return previousBoundTransforms;
    }

    @Override
    public ComponentAttachable getOwner() {
        return owner;
    }

    @Override
    public ArmatureComponent getRenewed(final ComponentAttachable newOwner) {
        // No deep-copying the component. Just change the owner, the animator's along with it.
        owner = newOwner;
        animator.setOwner(newOwner);
        clearRagdoll();
        return this;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Map<String, PatchComponent> getComponents() {
        return components;
    }

    @Override
    public LogicalSide logicalSide() {
        return owner.logicalSide();
    }

    @SuppressWarnings("unchecked") @Override
    public @Nullable <C extends UniqueComponent> C getComponent(Class<C> type) {
        UniqueComponent component = uniqueComponents.get(type);

        return component != null && component.isEnabled() ? (C)component : null;
    }

    /// A bone's transform in this armature's model space, at the pose [#updateBoundTransforms] last
    /// wrote. Identity before the first update.
    public Transform getBoneModelTransform(final String boneName) {
        ensureServerPose();

        AssetAccessor<? extends Armature> accessor = getArmature();
        Armature resolved = accessor == null ? null : accessor.get();

        if (resolved == null || boundTransforms == null) return Transform.identity();

        Bone bone = resolved.getBoneByName(boneName);

        if (bone == null || bone.getId() >= boundTransforms.length) return Transform.identity();

        return Transform.fromMatrix(boundTransforms[bone.getId()]);
    }

    /// A bone's transform in this armature's model space one tick back—the far end of a sweep.
    ///
    /// The current transform whenever there is no such tick: on a client, before a second pose, and
    /// after a gap in which nothing asked this armature for anything. A sweep across a gap would span
    /// motion no query witnessed, so it collapses to a point instead.
    public Transform getPreviousBoneModelTransform(final String boneName) {
        ensureServerPose();

        AssetAccessor<? extends Armature> accessor = getArmature();
        Armature resolved = accessor == null ? null : accessor.get();

        if (resolved == null || previousBoundTransforms == null || previousPoseTick != poseTick - 1L) {
            return getBoneModelTransform(boneName);
        }

        Bone bone = resolved.getBoneByName(boneName);

        if (bone == null || bone.getId() >= previousBoundTransforms.length) return Transform.identity();

        return Transform.fromMatrix(previousBoundTransforms[bone.getId()]);
    }

    /// Returns a bound transform of socket by name, in this armature's model space.
    public @Nullable Transform getSocketBoundTransform(final String socketName) {
        return components.get(socketName) instanceof TransformSocket socket ? socket.getBoundTransform() : null;
    }

    // ***************************************************************
    // TransformComponent
    // ***************************************************************

    @Override
    public Transform localTransform() {
        return localTransform;
    }

    @Override
    public void setLocalTransform(final Transform transform) {
        localTransform = transform;
    }

    @Override
    public ComponentAttachable host() {
        return owner;
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return transformInheritPolicy;
    }
}
