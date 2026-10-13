package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TickableDynamicComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

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
public class ArmatureComponent
    implements DynamicPatchComponent<ComponentAttachable>, TickableDynamicComponent<ComponentAttachable>, ComponentAttachable, TransformComponent
{
    /// One server tick, in seconds. The client passes real frame time instead.
    private static final float SERVER_FRAME_TIME = 0.05F;

    private final AssetAccessor<? extends Armature> armature;
    private final Animator animator;
    private final @Nullable RootMotionComponent rootMotionComponent;

    /// The attached components by name, in declaration order—[TransformSocket]s and whatever draws
    /// the entity's body.
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

    public ArmatureComponent(
        final ComponentAttachable owner,
        final AssetAccessor<? extends Armature> armature,
        final Animator animator,
        final boolean useRootMotion
    ) {
        this(owner, armature, animator, useRootMotion, Transform.IDENTITY, TransformInheritPolicy.LOC_ROT_SCA, Map.of());
    }

    public ArmatureComponent(
        final ComponentAttachable owner,
        final AssetAccessor<? extends Armature> armature,
        final Animator animator,
        final boolean useRootMotion,
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy,
        final Map<String, ComponentBlueprint<?, ?>> childBlueprints
    ) {
        this.armature = armature;
        this.animator = animator;
        this.owner = owner;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;

        ComponentBlueprint.instantiateAll(childBlueprints, this, owner.logicalSide().validInClientSide(), components::put);

        uniqueComponents = ComponentAttachable.indexUnique(components);

        rootMotionComponent = useRootMotion ? new RootMotionComponent(this) : null;
    }

    @Override
    public void tick() {
        // The client advances animation time in [#frameUpdate], once per frame. A server has no frame,
        // so its tick does it—before [Animator#tick], whose notify window is
        // [lastUpdatedTime, elapsedTime] and so has to see the time this tick moved to.
        //
        // Time only. The skeleton itself is built on demand, in [#ensureServerPose].
        if (owner.logicalSide() == LogicalSide.SERVER) {
            getAnimator().update(SERVER_FRAME_TIME, 1.0F);
        }

        getAnimator().tick();
        if (rootMotionComponent != null) rootMotionComponent.tick();
    }

    @Override
    public void frameUpdate(float frameTime, float partialTick) {
        getAnimator().update(frameTime, partialTick);
        updateBoundTransforms();

        if (rootMotionComponent != null) rootMotionComponent.frameUpdate(frameTime);
    }

    /// Rewrites [#boundTransforms] from the animator's current pose.
    public void updateBoundTransforms() {
        AssetAccessor<? extends Armature> accessor = getArmature();
        Armature resolved = accessor == null ? null : accessor.get();

        if (resolved == null) return;

        if (boundTransforms == null || boundTransforms.length != resolved.getBoneCount()) {
            boundTransforms = Matrix4fUtils.allocateMatrixArray(resolved.getBoneCount());
        }

        cachedPose = getAnimator().getPose();
        cachedPose.getForwardBoundTransforms(accessor, boundTransforms);
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

    // The call site must be in-game time. Crashed when try to use this component in preview model.
    public @Nullable RootMotionComponent getRootMotionComponent() {
        return rootMotionComponent;
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
