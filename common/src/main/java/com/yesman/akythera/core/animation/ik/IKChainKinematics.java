package com.yesman.akythera.core.animation.ik;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akythera.core.asset.physics.constraint.LimitRotationConstraint;
import com.yesman.akythera.core.math.Transform;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/// The bones of one [IKChain] in a [Pose], in armature space, which an [IKSolver] turns. Every turn rewrites the pose
/// and keeps the bone inside its [LimitRotationConstraint], whatever [LimitRotationConstraint#simulationOnly] says.
public final class IKChainKinematics {
    /// Shortest direction a bone is aimed along, in blocks. Shorter ones leave the bone as it is.
    private static final double MIN_AIM_LENGTH = 1.0E-6D;

    private final Pose pose;

    /// The chain's bones, from its root to its tip.
    private final Bone[] bones;

    /// The rotation limit of each of [#bones], null for a bone without one.
    private final @Nullable LimitRotationConstraint[] limits;

    /// The armature-space transform of the chain root's parent, identity for a chain starting at the armature's root.
    private final Matrix4d baseFrame;

    /// The armature-space transform of each of [#bones] in [#pose].
    private final Matrix4d[] models;

    /// The head of each of [#bones] in armature space, then the tip bone's tail.
    private final Vector3d[] joints;

    /// The length of each of [#bones]: from its head to the next joint.
    private final double[] lengths;

    private IKChainKinematics(final Pose pose, final Bone[] bones, final @Nullable LimitRotationConstraint[] limits, final Matrix4d baseFrame) {
        this.pose = pose;
        this.bones = bones;
        this.limits = limits;
        this.baseFrame = baseFrame;
        models = new Matrix4d[bones.length];
        joints = new Vector3d[bones.length + 1];
        lengths = new double[bones.length];

        for (int i = 0; i < bones.length; i++) models[i] = new Matrix4d();
        for (int i = 0; i <= bones.length; i++) joints[i] = new Vector3d();

        updateFrom(0);

        for (int i = 0; i < bones.length; i++) lengths[i] = joints[i].distance(joints[i + 1]);
    }

    /// The chain of `chainLength` bones ending at `tipBone` in `pose`, or null when the armature has no such bone or
    /// the bone has fewer ancestors than the chain needs.
    public static @Nullable IKChainKinematics of(
        final Armature armature, final Pose pose, final String tipBone, final int chainLength, final ArmaturePhysicsData physics
    ) {
        Bone tip = armature.getBoneByName(tipBone);

        if (tip == null || chainLength < 1) return null;

        Bone[] bones = new Bone[chainLength];
        Bone walked = tip;

        for (int i = chainLength - 1; i >= 0; i--) {
            if (walked == null) return null;

            bones[i] = walked;
            walked = walked.getParent();
        }

        @Nullable LimitRotationConstraint[] limits = new LimitRotationConstraint[chainLength];

        for (int i = 0; i < chainLength; i++) {
            List<BoneConstraint> constraints = physics.constraints().getOrDefault(bones[i].getName(), List.of());

            for (BoneConstraint constraint : constraints) {
                if (constraint instanceof LimitRotationConstraint limit && limit.limitsRotation()) limits[i] = limit;
            }
        }

        Deque<Bone> ancestors = new ArrayDeque<>();

        for (Bone ancestor = bones[0].getParent(); ancestor != null; ancestor = ancestor.getParent()) ancestors.push(ancestor);

        Matrix4d baseFrame = new Matrix4d();

        for (Bone ancestor : ancestors) baseFrame.mul(boneMatrix(ancestor, pose));

        return new IKChainKinematics(pose, bones, limits, baseFrame);
    }

    /// The number of bones in the chain.
    public int length() {
        return bones.length;
    }

    /// Joint `index` in armature space: the head of bone `index`, or the tip bone's tail for [#length].
    public Vector3d joint(final int index) {
        return joints[index];
    }

    /// The distance from the head of bone `index` to the next joint.
    public double boneLength(final int index) {
        return lengths[index];
    }

    /// Turns bone `index` around its head so the next joint lies toward `target`, then holds it inside its limit. The
    /// bones after it follow.
    public void aim(final int index, final Vector3d target) {
        Vector3d current = joints[index + 1].sub(joints[index], new Vector3d());
        Vector3d desired = target.sub(joints[index], new Vector3d());

        if (current.length() < MIN_AIM_LENGTH || desired.length() < MIN_AIM_LENGTH) return;

        Quaterniond turn = new Quaterniond().rotationTo(current.normalize(), desired.normalize());
        Matrix4d parent = index == 0 ? baseFrame : models[index - 1];
        Quaterniond frame = parent.getNormalizedRotation(new Quaterniond())
            .mul(new Matrix4d(bones[index].getLocalTransform()).getNormalizedRotation(new Quaterniond()));
        Transform transform = pose.orElseIdentity(bones[index].getName());
        Quaterniond rotated = new Quaterniond(frame).conjugate().mul(turn).mul(frame).mul(transform.rot()).normalize();
        LimitRotationConstraint limit = limits[index];

        if (limit != null) limit.limit(rotated, rotated);

        pose.putBoneTransform(bones[index].getName(), new Transform(transform.loc(), rotated, transform.sca()));
        updateFrom(index);
    }

    /// Rewrites [#models] and [#joints] from bone `index` on, from [#pose].
    private void updateFrom(final int index) {
        for (int i = index; i < bones.length; i++) {
            Matrix4d parent = i == 0 ? baseFrame : models[i - 1];
            parent.mul(boneMatrix(bones[i], pose), models[i]);
            models[i].getTranslation(joints[i]);
        }

        Bone tip = bones[bones.length - 1];
        models[bones.length - 1].transformPosition(new Vector3d(tip.getToTail()), joints[bones.length]);
    }

    /// `bone`'s transform in its parent's space under `pose`: its rest transform, then its pose transform.
    private static Matrix4d boneMatrix(final Bone bone, final Pose pose) {
        Transform transform = pose.orElseIdentity(bone.getName());

        return new Matrix4d(bone.getLocalTransform())
            .translate(transform.loc().x(), transform.loc().y(), transform.loc().z())
            .rotate(transform.rot())
            .scale(transform.sca().x(), transform.sca().y(), transform.sca().z());
    }
}
