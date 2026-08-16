package com.yesman.akythera.api.component.impl;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.DynamicPatchComponent;
import com.yesman.akythera.api.component.TickableDynamicComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// Module for skeletal mesh armature. Animator and armature are paired since both are coupled to work.
///
/// Implements [ComponentAttachable], Used in adding sockets. Only socket blueprints are allowed.
public class ArmatureComponent
    implements DynamicPatchComponent<EntityPatch<Entity>>, TickableDynamicComponent<EntityPatch<Entity>>, ComponentAttachable, UniqueComponent
{
    private final AssetAccessor<? extends Armature> armature;
    private final Animator animator;
    private final @Nullable RootMotionComponent rootMotionComponent;

    /// List of attached [BoneSocket]
    private final Map<String, BoneSocket> boneSockets = new HashMap<>();

    private EntityPatch<Entity> owner;

    /// Last pose resulted by [Animator#getPose()]
    ///
    /// Access pose via this field instead of calling [Animator#getPose()] every time.
    private Pose cachedPose;

    /// The forward-bound skeleton at the pose [#updateBoundTransforms] last wrote. Sized on the
    /// first update: a preview armature has no armature yet when its constructor runs.
    private Matrix4f @Nullable [] boundTransforms;

    public ArmatureComponent(
        final EntityPatch<Entity> owner,
        final AssetAccessor<? extends Armature> armature,
        final Animator animator,
        final boolean useRootMotion
    ) {
        this(owner, armature, animator, useRootMotion, Map.of());
    }

    public ArmatureComponent(
        final EntityPatch<Entity> owner,
        final AssetAccessor<? extends Armature> armature,
        final Animator animator,
        final boolean useRootMotion,
        final Map<String, BoneSocket> sockets
    ) {
        this.armature = armature;
        this.animator = animator;
        this.owner = owner;

        sockets.forEach((name, socket) -> boneSockets.put(name, socket.instantiate(this)));

        rootMotionComponent = useRootMotion ? new RootMotionComponent(owner) : null;
    }

    @Override
    public void tick() {
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

    // The call site must be in-game time. Crashed when try to use this component in preview model.
    public @Nullable RootMotionComponent getRootMotionComponent() {
        return rootMotionComponent;
    }

    // Null if preview armature component
    @Override
    public EntityPatch<Entity> getOwner() {
        return owner;
    }

    @Override
    public ArmatureComponent getRenewed(final EntityPatch<Entity> newOwner) {
        // No deep-copying the component. Just change the owner.
        owner = newOwner;
        return this;
    }

    @Override
    public Map<String, BoneSocket> getComponents() {
        return boneSockets;
    }

    /// Armature component doesn't support type-based component getter—all sockets
    /// are meant to be identified by string.
    @Override @Deprecated
    public @Nullable <C extends UniqueComponent> C getComponent(Class<C> type) {
        return null;
    }

    /// A bone's transform in this armature's model space, at the pose [#updateBoundTransforms] last
    /// wrote. Identity before the first update.
    public Transform getBoneModelTransform(final String boneName) {
        AssetAccessor<? extends Armature> accessor = getArmature();
        Armature resolved = accessor == null ? null : accessor.get();

        if (resolved == null || boundTransforms == null) return Transform.identity();

        Bone bone = resolved.getBoneByName(boneName);

        if (bone == null || bone.getId() >= boundTransforms.length) return Transform.identity();

        return Transform.fromMatrix(boundTransforms[bone.getId()]);
    }

    /// Returns a bound transform of socket by name, in this armature's model space.
    public @Nullable Transform getSocketBoundTransform(final String socketName) {
        BoneSocket socket = boneSockets.get(socketName);
        return socket == null ? null : socket.getBoundTransform();
    }
}
