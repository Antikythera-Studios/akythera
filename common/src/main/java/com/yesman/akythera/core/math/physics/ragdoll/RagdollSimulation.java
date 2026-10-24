package com.yesman.akythera.core.math.physics.ragdoll;

import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.CapsuleCollider;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.OrientedBoxCollider;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// A rag doll: one rigid body per [ArmaturePhysicsData.BoneBody], linked at the bone heads and kept inside the
/// [ArmaturePhysicsData.BoneConstraint]s, falling over the colliders of a [Terrain].
///
/// Solved on the CPU by extended position based dynamics (XPBD), [ArmaturePhysicsData.RagdollParameters#substeps]
/// substeps per game tick. Positions are kept relative to [#origin], where the armature stood at start, so the solver
/// works in small numbers wherever the level is.
///
/// A bone without a body keeps the pose it had at start: under a bodied bone it rides its parent, above every bodied
/// bone it rides the body closest to the root.
public final class RagdollSimulation {
    private static final double TICK_SECONDS = 0.05D;
    private static final double GRAVITY = 9.81D;

    /// Fastest a body may move, in blocks per second.
    private static final double MAX_SPEED = 40.0D;

    /// Fastest a body may spin, in radians per second.
    private static final double MAX_ANGULAR_SPEED = 60.0D;

    /// Ticks every body has to stay under the sleep speed before the simulation rests.
    private static final int SLEEP_TICKS = 20;

    /// Radius of the points standing for the corners of a box collider, in blocks.
    private static final double CORNER_RADIUS = 0.02D;

    /// Most spheres a capsule collider is sampled into.
    private static final int MAX_CAPSULE_SPHERES = 8;

    /// Most self-collision spheres a box collider is filled with along one of its axes.
    private static final int MAX_BOX_SPHERES_PER_AXIS = 4;

    /// How deep two bodies may sink into each other before they are pushed apart, in blocks. Spheres that only touch
    /// stay at rest instead of chattering.
    private static final double SELF_CONTACT_SLOP = 0.005D;

    /// Room added around a body's reach for the tick when the block colliders are gathered, in blocks.
    private static final double TERRAIN_MARGIN = 0.25D;

    private static final int BODY = 0;
    private static final int FOLLOW_PARENT = 1;
    private static final int FOLLOW_ANCHOR = 2;

    private final ArmaturePhysicsData.RagdollParameters parameters;

    /// What the bodies collide with, null for nothing.
    private final @Nullable Terrain terrain;

    /// World position every position of this simulation is relative to.
    private final Vec3 origin;

    /// The bones in hierarchy order, each parent before its children.
    private final Bone[] bones;

    /// Index into [#bones] of each bone's parent, -1 for the root.
    private final int[] parents;

    /// How each bone of [#bones] is placed: [#BODY], [#FOLLOW_PARENT] or [#FOLLOW_ANCHOR].
    private final int[] placements;

    /// The body of each bone of [#bones], null for a bone that has none.
    private final @Nullable Body[] bodyOfBone;

    /// [#FOLLOW_PARENT]: the bone's transform in its parent's space. [#FOLLOW_ANCHOR]: in [#anchor]'s bone space.
    private final Matrix4d[] frozenOffsets;

    /// Inverse of each bone's [Bone#getLocalTransform].
    private final Matrix4d[] inverseLocals;

    private final Body[] bodies;
    private final Joint[] joints;

    /// The body closest to the root, which carries the bones above every body.
    private final Body anchor;

    /// For two bodies `i < j`, the pairs of their self-collision spheres that are not pushed apart, bit
    /// `a * j's sphere count + b`; null when there are none:
    /// - pairs that overlapped at start, so bodies that touch in the bind pose do not burst apart, until the pair
    ///   separates once;
    /// - for two bodies a joint links, pairs with a sphere within reach of the joint, for good: the joint holds those
    ///   spheres together, and pushing them apart would fight it forever.
    private final @Nullable BitSet[][] restingOverlaps;

    /// Whether a joint links bodies `i < j`.
    private final boolean[][] linked;

    private final List<Contact> contacts = new ArrayList<>();
    private final List<SelfContact> selfContacts = new ArrayList<>();

    private int calmTicks;
    private boolean sleeping;

    /// The colliders a rag doll falls over.
    @FunctionalInterface
    public interface Terrain {
        /// Hands every collider box inside the world-space `area` to `sink`, in world space. False when part of `area`
        /// is not loaded.
        boolean collect(AABB area, Consumer<AABB> sink);

        /// The block colliders of `level`.
        static Terrain of(final Level level) {
            return (area, sink) -> {
                if (!level.hasChunksAt(Mth.floor(area.minX), Mth.floor(area.minZ), Mth.floor(area.maxX), Mth.floor(area.maxZ))) {
                    return false;
                }

                for (VoxelShape shape : level.getBlockCollisions(null, area)) {
                    shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> sink.accept(new AABB(x0, y0, z0, x1, y1, z1)));
                }

                return true;
            };
        }

        /// An endless floor whose top is at `height`.
        static Terrain floor(final double height) {
            return (area, sink) -> {
                if (area.minY < height) sink.accept(new AABB(area.minX, area.minY - 1.0D, area.minZ, area.maxX, height, area.maxZ));

                return true;
            };
        }
    }

    /// The armature as it was drawn at one moment.
    ///
    /// @param pose            the animation pose
    /// @param armatureToWorld where the armature stood in the world
    /// @param time            when, in seconds on any clock both snapshots share
    public record Snapshot(Pose pose, Transform armatureToWorld, double time) {}

    private RagdollSimulation(
        final ArmaturePhysicsData.RagdollParameters parameters,
        final @Nullable Terrain terrain,
        final Vec3 origin,
        final Bone[] bones,
        final int[] parents,
        final int[] placements,
        final @Nullable Body[] bodyOfBone,
        final Matrix4d[] frozenOffsets,
        final Matrix4d[] inverseLocals,
        final Body[] bodies,
        final Joint[] joints,
        final @Nullable BitSet[][] restingOverlaps,
        final boolean[][] linked
    ) {
        this.parameters = parameters;
        this.terrain = terrain;
        this.origin = origin;
        this.bones = bones;
        this.parents = parents;
        this.placements = placements;
        this.bodyOfBone = bodyOfBone;
        this.frozenOffsets = frozenOffsets;
        this.inverseLocals = inverseLocals;
        this.bodies = bodies;
        this.joints = joints;
        this.anchor = bodies[0];
        this.restingOverlaps = restingOverlaps;
        this.linked = linked;
    }

    /// Builds the bodies where `current` draws them, moving as the armature moved from `previous` to `current`
    /// times `velocityScale`. Null when no bone of `armature` has a body in `physics`.
    ///
    /// @param previous an earlier snapshot to take the bodies' velocities from, null to start them still
    public static @Nullable RagdollSimulation start(
        final Armature armature,
        final ArmaturePhysicsData physics,
        final @Nullable Terrain terrain,
        final @Nullable Snapshot previous,
        final Snapshot current,
        final float velocityScale
    ) {
        List<Bone> ordered = new ArrayList<>();
        armature.getRootBone().iterateChildBones(ordered::add);

        int count = ordered.size();
        Bone[] bones = ordered.toArray(new Bone[0]);
        int[] parents = new int[count];
        Map<Bone, Integer> indexOf = new HashMap<>();

        for (int i = 0; i < count; i++) indexOf.put(bones[i], i);

        parents[0] = -1;

        for (int i = 0; i < count; i++) {
            for (Bone child : bones[i].getChildBones()) parents[indexOf.get(child)] = i;
        }

        Vec3 origin = current.armatureToWorld().loc();
        Matrix4d[] world = worldTransforms(armature, bones, current, origin);
        Matrix4d[] worldBefore = previous == null ? null : worldTransforms(armature, bones, previous, origin);
        double elapsed = previous == null ? 0.0D : current.time() - previous.time();
        boolean moving = worldBefore != null && elapsed > 1.0E-3D;

        int[] placements = new int[count];
        Body[] bodyOfBone = new Body[count];
        List<Body> bodies = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ArmaturePhysicsData.BoneBody authored = physics.bodies().get(bones[i].getName());

            if (authored == null || authored.colliders().isEmpty()) continue;

            Body body = Body.build(i, authored, world[i]);

            if (moving) body.takeVelocity(worldBefore[i], world[i], elapsed, velocityScale);

            bodyOfBone[i] = body;
            placements[i] = BODY;
            bodies.add(body);
        }

        if (bodies.isEmpty()) return null;

        Body anchor = bodies.getFirst();
        Matrix4d[] frozenOffsets = new Matrix4d[count];
        Matrix4d[] inverseLocals = new Matrix4d[count];
        List<Joint> joints = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            inverseLocals[i] = new Matrix4d(bones[i].getLocalTransform()).invert();

            int bodiedAncestor = parents[i];

            while (bodiedAncestor >= 0 && bodyOfBone[bodiedAncestor] == null) bodiedAncestor = parents[bodiedAncestor];

            if (bodyOfBone[i] != null) {
                if (bodyOfBone[i] == anchor) continue;

                // A body under no bodied bone hangs off bones that ride the anchor, so it links to the anchor
                joints.add(Joint.build(
                    bodiedAncestor >= 0 ? bodyOfBone[bodiedAncestor] : anchor, bodyOfBone[i], world[i],
                    new Matrix4d(world[parents[i]]).mul(new Matrix4d(bones[i].getLocalTransform())),
                    physics.constraints().get(bones[i].getName())
                ));
            } else if (bodiedAncestor >= 0) {
                placements[i] = FOLLOW_PARENT;
                frozenOffsets[i] = new Matrix4d(world[parents[i]]).invert().mul(world[i]);
            } else {
                placements[i] = FOLLOW_ANCHOR;
                frozenOffsets[i] = new Matrix4d(world[anchor.boneIndex]).invert().mul(world[i]);
            }
        }

        Body[] bodyArray = bodies.toArray(new Body[0]);
        BitSet[][] restingOverlaps = new BitSet[bodyArray.length][bodyArray.length];
        boolean[][] linked = new boolean[bodyArray.length][bodyArray.length];
        List<Body> order = List.of(bodyArray);

        for (int i = 0; i < bodyArray.length; i++) {
            for (int j = i + 1; j < bodyArray.length; j++) {
                BitSet overlapping = bodyArray[i].overlappingSelfSpheres(bodyArray[j]);

                if (!overlapping.isEmpty()) restingOverlaps[i][j] = overlapping;
            }
        }

        for (Joint joint : joints) {
            int parent = order.indexOf(joint.parent);
            int child = order.indexOf(joint.child);
            int i = Math.min(parent, child);
            int j = Math.max(parent, child);
            Vector3d head = joint.child.rotation.transform(joint.childAnchor, new Vector3d()).add(joint.child.position);
            BitSet nearJoint = bodyArray[i].selfSpheresNearPoint(bodyArray[j], head);

            linked[i][j] = true;

            if (nearJoint.isEmpty()) continue;

            if (restingOverlaps[i][j] == null) {
                restingOverlaps[i][j] = nearJoint;
            } else {
                restingOverlaps[i][j].or(nearJoint);
            }
        }

        return new RagdollSimulation(
            physics.simulation(), terrain, origin, bones, parents, placements, bodyOfBone,
            frozenOffsets, inverseLocals, bodyArray, joints.toArray(new Joint[0]), restingOverlaps, linked
        );
    }

    /// Each bone's world transform in `snapshot`, relative to `origin`.
    private static Matrix4d[] worldTransforms(final Armature armature, final Bone[] bones, final Snapshot snapshot, final Vec3 origin) {
        Matrix4f[] bound = Matrix4fUtils.allocateMatrixArray(armature.getBoneCount());
        snapshot.pose().boundTransformRecursively(armature.getRootBone(), new Matrix4f(), bound);

        Matrix4d armatureToWorld = relativeMatrix(snapshot.armatureToWorld(), origin);
        Matrix4d[] world = new Matrix4d[bones.length];

        for (int i = 0; i < bones.length; i++) {
            world[i] = new Matrix4d(armatureToWorld).mul(new Matrix4d(bound[bones[i].getId()]));
        }

        return world;
    }

    /// `transform` as a matrix, with its translation taken relative to `origin`.
    private static Matrix4d relativeMatrix(final Transform transform, final Vec3 origin) {
        return new Matrix4d()
            .translation(transform.loc().x - origin.x, transform.loc().y - origin.y, transform.loc().z - origin.z)
            .rotate(transform.rot())
            .scale(transform.sca().x, transform.sca().y, transform.sca().z);
    }

    // ***************************************************************
    // Simulation
    // ***************************************************************

    /// Advances the bodies by one game tick.
    public void tick() {
        for (Body body : bodies) {
            body.tickPosition.set(body.position);
            body.tickRotation.set(body.rotation);
        }

        if (sleeping) return;

        if (!gatherTerrain()) {
            rest();
            return;
        }

        int substeps = parameters.substeps();
        double h = TICK_SECONDS / substeps;

        for (int i = 0; i < substeps; i++) substep(h);

        double fastest = 0.0D;

        for (Body body : bodies) {
            fastest = Math.max(fastest, Math.max(body.velocity.length(), body.angularVelocity.length() * body.boundingRadius));
        }

        calmTicks = fastest < parameters.sleepSpeed() ? calmTicks + 1 : 0;

        if (calmTicks >= SLEEP_TICKS) rest();
    }

    /// Whether the bodies have come to rest and no longer move.
    public boolean isSleeping() {
        return sleeping;
    }

    /// The world position of the body closest to the root's center of mass, at the end of the current tick.
    public Vec3 anchorPosition() {
        return origin.add(anchor.position.x, anchor.position.y, anchor.position.z);
    }

    /// Pushes the body of `boneName` by `impulse` at `worldPoint`, null for its center of mass. A bone without a body
    /// passes the push to its closest bodied ancestor, or to the body closest to the root.
    public void applyImpulse(final String boneName, final Vec3 impulse, final @Nullable Vec3 worldPoint) {
        Body body = bodyOf(boneName);
        Vector3d push = new Vector3d(impulse.x, impulse.y, impulse.z);
        body.velocity.fma(body.invMass, push);

        if (worldPoint != null) {
            Vector3d arm = new Vector3d(worldPoint.x - origin.x, worldPoint.y - origin.y, worldPoint.z - origin.z).sub(body.position);
            body.angularVelocity.add(body.applyInverseInertia(arm.cross(push), new Vector3d()));
        }

        wake();
    }

    /// The body of `boneName`, of its closest bodied ancestor, or the anchor.
    private Body bodyOf(final String boneName) {
        for (int i = 0; i < bones.length; i++) {
            if (!bones[i].getName().equals(boneName)) continue;

            int bodied = i;

            while (bodied >= 0 && bodyOfBone[bodied] == null) bodied = parents[bodied];

            return bodied >= 0 ? bodyOfBone[bodied] : anchor;
        }

        return anchor;
    }

    /// Adds `velocity` to every body alike, in blocks per second.
    public void addVelocity(final Vec3 velocity) {
        for (Body body : bodies) body.velocity.add(velocity.x, velocity.y, velocity.z);

        wake();
    }

    private void wake() {
        sleeping = false;
        calmTicks = 0;
    }

    /// Stops the bodies where they stand. An impulse wakes them.
    public void rest() {
        sleeping = true;

        for (Body body : bodies) {
            body.velocity.zero();
            body.angularVelocity.zero();
        }
    }

    /// Gathers the block colliders each body may reach this tick. False when a body reaches into a chunk that is not
    /// loaded.
    private boolean gatherTerrain() {
        for (Body body : bodies) body.terrain.clear();

        if (terrain == null) return true;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Body body : bodies) {
            double reach = body.boundingRadius + body.velocity.length() * TICK_SECONDS + TERRAIN_MARGIN;
            body.reach = new AABB(
                body.position.x - reach, body.position.y - reach, body.position.z - reach,
                body.position.x + reach, body.position.y + reach, body.position.z + reach
            );
            minX = Math.min(minX, body.reach.minX);
            minY = Math.min(minY, body.reach.minY);
            minZ = Math.min(minZ, body.reach.minZ);
            maxX = Math.max(maxX, body.reach.maxX);
            maxY = Math.max(maxY, body.reach.maxY);
            maxZ = Math.max(maxZ, body.reach.maxZ);
        }

        List<AABB> boxes = new ArrayList<>();
        Vec3 back = origin.reverse();

        if (!terrain.collect(new AABB(minX, minY, minZ, maxX, maxY, maxZ).move(origin), box -> boxes.add(box.move(back)))) {
            return false;
        }

        for (Body body : bodies) {
            for (AABB box : boxes) {
                if (box.intersects(body.reach)) body.terrain.add(box);
            }
        }

        return true;
    }

    private void substep(final double h) {
        double gravity = GRAVITY * parameters.gravityScale();
        Vector3d spin = new Vector3d();

        for (Body body : bodies) {
            body.previousPosition.set(body.position);
            body.previousRotation.set(body.rotation);

            body.velocity.y -= gravity * h;

            if (body.velocity.length() > MAX_SPEED) body.velocity.normalize(MAX_SPEED);
            if (body.angularVelocity.length() > MAX_ANGULAR_SPEED) body.angularVelocity.normalize(MAX_ANGULAR_SPEED);

            body.position.fma(h, body.velocity);
            addRotation(body.rotation, body.angularVelocity.mul(h, spin), 0.5D);
        }

        contacts.clear();

        for (Joint joint : joints) solveJoint(joint);

        for (Body body : bodies) solveTerrain(body);

        if (parameters.selfCollision()) solveSelfCollision();

        Quaterniond turn = new Quaterniond();

        for (Body body : bodies) {
            body.position.sub(body.previousPosition, body.velocity).div(h);
            body.rotation.mul(body.previousRotation.conjugate(turn), turn);
            body.angularVelocity.set(turn.x, turn.y, turn.z).mul(2.0D / h);

            if (turn.w < 0.0D) body.angularVelocity.negate();

            body.velocity.mul(Math.max(0.0D, 1.0D - body.linearDamping * h));
            body.angularVelocity.mul(Math.max(0.0D, 1.0D - body.angularDamping * h));
        }

        double bounceSpeed = 2.0D * gravity * h;

        for (Contact contact : contacts) {
            Body body = contact.body;
            Vector3d pointVelocity = new Vector3d(body.angularVelocity).cross(contact.arm).add(body.velocity);
            double normalSpeed = pointVelocity.dot(contact.normal);
            double target = contact.approachSpeed < -bounceSpeed ? -parameters.restitution() * contact.approachSpeed : 0.0D;

            if (normalSpeed >= target) continue;

            double weight = body.generalizedInverseMass(contact.arm, contact.normal);

            if (weight <= 0.0D) continue;

            Vector3d impulse = new Vector3d(contact.normal).mul((target - normalSpeed) / weight);
            body.velocity.fma(body.invMass, impulse);
            body.angularVelocity.add(body.applyInverseInertia(new Vector3d(contact.arm).cross(impulse), new Vector3d()));
        }

        // Two bodies pushed apart this substep stop moving along the normal relative to each other
        for (SelfContact contact : selfContacts) {
            Body a = contact.a;
            Body b = contact.b;
            Vector3d velocityA = new Vector3d(a.angularVelocity).cross(contact.armA).add(a.velocity);
            Vector3d velocityB = new Vector3d(b.angularVelocity).cross(contact.armB).add(b.velocity);
            double normalSpeed = velocityB.sub(velocityA).dot(contact.normal);
            double weight = a.generalizedInverseMass(contact.armA, contact.normal) + b.generalizedInverseMass(contact.armB, contact.normal);

            if (weight <= 0.0D) continue;

            Vector3d impulse = new Vector3d(contact.normal).mul(normalSpeed / weight);
            a.velocity.fma(a.invMass, impulse);
            a.angularVelocity.add(a.applyInverseInertia(new Vector3d(contact.armA).cross(impulse), new Vector3d()));
            b.velocity.fma(-b.invMass, impulse);
            b.angularVelocity.sub(b.applyInverseInertia(new Vector3d(contact.armB).cross(impulse), new Vector3d()));
        }
    }

    private void solveJoint(final Joint joint) {
        Body parent = joint.parent;
        Body child = joint.child;
        Vector3d parentArm = parent.rotation.transform(joint.parentAnchor, new Vector3d());
        Vector3d childArm = child.rotation.transform(joint.childAnchor, new Vector3d());
        Vector3d gap = new Vector3d(child.position).add(childArm).sub(parent.position).sub(parentArm);
        double distance = gap.length();

        if (distance > 1.0E-9D) {
            correctPosition(parent, parentArm, child, childArm, gap.div(distance), distance);
        }

        if (joint.constraint == null || !joint.constraint.limitsRotation()) return;

        Quaterniond frame = parent.rotation.mul(joint.frame, new Quaterniond());
        Quaterniond local = frame.conjugate(new Quaterniond()).mul(child.rotation);
        Quaterniond limited = new Quaterniond();

        if (!limitRotation(local, joint.constraint, limited)) return;

        Quaterniond delta = limited.mul(local.conjugate(), new Quaterniond());

        if (delta.w < 0.0D) delta.set(-delta.x, -delta.y, -delta.z, -delta.w);

        double sine = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);

        if (sine < 1.0E-9D) return;

        double angle = 2.0D * Math.atan2(sine, delta.w);
        Vector3d axis = frame.transform(new Vector3d(delta.x / sine, delta.y / sine, delta.z / sine));

        correctRotation(parent, child, axis, angle);
    }

    /// Writes into `dest` the rotation `local` is limited to by `constraint`. False when `local` is inside the limits
    /// already.
    ///
    /// `local` splits into a twist around Y and a swing after it; the swing's rotation vector is limited by its X and Z
    /// parts, and the twist by its angle.
    static boolean limitRotation(final Quaterniond local, final ArmaturePhysicsData.BoneConstraint constraint, final Quaterniond dest) {
        Quaterniond rotation = new Quaterniond(local);

        if (rotation.w < 0.0D) rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);

        double twistLength = Math.sqrt(rotation.y * rotation.y + rotation.w * rotation.w);
        Quaterniond twist = twistLength < 1.0E-9D
            ? new Quaterniond()
            : new Quaterniond(0.0D, rotation.y / twistLength, 0.0D, rotation.w / twistLength);
        Quaterniond swing = rotation.mul(twist.conjugate(new Quaterniond()), new Quaterniond());

        if (swing.w < 0.0D) swing.set(-swing.x, -swing.y, -swing.z, -swing.w);

        double twistAngle = 2.0D * Math.atan2(twist.y, twist.w);
        double swingSine = Math.sqrt(swing.x * swing.x + swing.z * swing.z);
        double swingAngle = 2.0D * Math.atan2(swingSine, swing.w);
        double swingX = swingSine < 1.0E-9D ? 0.0D : swing.x / swingSine * swingAngle;
        double swingZ = swingSine < 1.0E-9D ? 0.0D : swing.z / swingSine * swingAngle;

        double limitedX = clamp(swingX, constraint.x());
        double limitedY = clamp(twistAngle, constraint.y());
        double limitedZ = clamp(swingZ, constraint.z());

        if (limitedX == swingX && limitedZ == swingZ && limitedY == twistAngle) return false;

        double limitedSwing = Math.sqrt(limitedX * limitedX + limitedZ * limitedZ);
        Quaterniond limitedSwingRotation = limitedSwing < 1.0E-9D
            ? new Quaterniond()
            : new Quaterniond().rotationAxis(limitedSwing, limitedX / limitedSwing, 0.0D, limitedZ / limitedSwing);

        limitedSwingRotation.mul(new Quaterniond().rotationY(limitedY), dest);

        return true;
    }

    private static double clamp(final double radians, final ArmaturePhysicsData.AngularLimit limit) {
        if (limit.isFree()) return radians;

        return Math.clamp(radians, Math.toRadians(limit.min()), Math.toRadians(limit.max()));
    }

    private void solveTerrain(final Body body) {
        if (body.terrain.isEmpty()) return;

        Vector3d center = new Vector3d();
        Vector3d before = new Vector3d();

        for (int i = 0; i < body.proxyCenters.length; i++) {
            Vector3d proxy = body.proxyCenters[i];
            double radius = body.proxyRadii[i];

            for (AABB box : body.terrain) {
                body.rotation.transform(proxy, center).add(body.position);
                body.previousRotation.transform(proxy, before).add(body.previousPosition);

                double minX = box.minX - radius, minY = box.minY - radius, minZ = box.minZ - radius;
                double maxX = box.maxX + radius, maxY = box.maxY + radius, maxZ = box.maxZ + radius;
                boolean startedInside = before.x > minX && before.x < maxX && before.y > minY && before.y < maxY && before.z > minZ && before.z < maxZ;
                int axis;
                double face;
                double sign;

                if (startedInside) {
                    double[] depths = {center.x - minX, maxX - center.x, center.y - minY, maxY - center.y, center.z - minZ, maxZ - center.z};
                    int best = -1;

                    for (int side = 0; side < 6; side++) {
                        if ((best < 0 || depths[side] < depths[best]) && !faceCovered(body, box, side, radius, center)) best = side;
                    }

                    if (depths[0] <= 0.0D || depths[1] <= 0.0D || depths[2] <= 0.0D || depths[3] <= 0.0D || depths[4] <= 0.0D || depths[5] <= 0.0D) continue;

                    if (best < 0) best = 3; // walled in on every side: up

                    axis = best / 2;
                    sign = best % 2 == 0 ? -1.0D : 1.0D;
                    face = best % 2 == 0 ? axisValue(minX, minY, minZ, axis) : axisValue(maxX, maxY, maxZ, axis);
                } else {
                    Vector3d motion = new Vector3d(center).sub(before);
                    double enter = 0.0D;
                    double exit = 1.0D;
                    axis = -1;
                    sign = 0.0D;
                    face = 0.0D;

                    for (int a = 0; a < 3; a++) {
                        double from = axisValue(before.x, before.y, before.z, a);
                        double delta = axisValue(motion.x, motion.y, motion.z, a);
                        double low = axisValue(minX, minY, minZ, a);
                        double high = axisValue(maxX, maxY, maxZ, a);

                        if (Math.abs(delta) < 1.0E-12D) {
                            if (from <= low || from >= high) {
                                exit = -1.0D;
                                break;
                            }

                            continue;
                        }

                        double t0 = (low - from) / delta;
                        double t1 = (high - from) / delta;
                        double near = Math.min(t0, t1);
                        double far = Math.max(t0, t1);

                        if (near > enter) {
                            enter = near;
                            axis = a;
                            sign = delta > 0.0D ? -1.0D : 1.0D;
                            face = delta > 0.0D ? low : high;
                        }

                        exit = Math.min(exit, far);
                    }

                    if (axis < 0 || enter > exit || exit < 0.0D) continue;
                }

                double depth = (face - axisValue(center.x, center.y, center.z, axis)) * sign;

                if (depth <= 0.0D) continue;

                Vector3d normal = new Vector3d(axis == 0 ? sign : 0.0D, axis == 1 ? sign : 0.0D, axis == 2 ? sign : 0.0D);
                Vector3d arm = new Vector3d(center).fma(-radius, normal).sub(body.position);
                double approachSpeed = new Vector3d(body.angularVelocity).cross(arm).add(body.velocity).dot(normal);
                double pushed = correctPosition(body, arm, null, null, normal, depth);

                applyFriction(body, proxy, normal, radius, pushed);
                contacts.add(new Contact(body, body.rotation.transform(new Vector3d(proxy)).fma(-radius, normal), normal, approachSpeed));
            }
        }
    }

    /// Whether the face `side` of `box` touches another of `body`'s block colliders, so pushing out through it would
    /// push into that one.
    private static boolean faceCovered(final Body body, final AABB box, final int side, final double radius, final Vector3d center) {
        double x = center.x, y = center.y, z = center.z;

        switch (side) {
            case 0 -> x = box.minX - radius - 1.0E-4D;
            case 1 -> x = box.maxX + radius + 1.0E-4D;
            case 2 -> y = box.minY - radius - 1.0E-4D;
            case 3 -> y = box.maxY + radius + 1.0E-4D;
            case 4 -> z = box.minZ - radius - 1.0E-4D;
            default -> z = box.maxZ + radius + 1.0E-4D;
        }

        for (AABB other : body.terrain) {
            if (other == box) continue;

            if (x > other.minX - radius && x < other.maxX + radius
                && y > other.minY - radius && y < other.maxY + radius
                && z > other.minZ - radius && z < other.maxZ + radius) {
                return true;
            }
        }

        return false;
    }

    private static double axisValue(final double x, final double y, final double z, final int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    /// Cancels the sliding of a contact point along the terrain this substep, up to the friction `pushed` allows.
    private void applyFriction(final Body body, final Vector3d proxy, final Vector3d normal, final double radius, final double pushed) {
        Vector3d local = body.rotation.transformInverse(new Vector3d(normal), new Vector3d()).mul(-radius).add(proxy);
        Vector3d now = body.rotation.transform(local, new Vector3d()).add(body.position);
        Vector3d then = body.previousRotation.transform(local, new Vector3d()).add(body.previousPosition);
        Vector3d slide = now.sub(then);
        slide.fma(-slide.dot(normal), normal);

        double length = slide.length();

        if (length < 1.0E-9D) return;

        Vector3d arm = body.rotation.transform(local, new Vector3d());
        Vector3d direction = slide.div(length);
        double weight = body.generalizedInverseMass(arm, direction);

        if (weight <= 0.0D) return;

        double stop = Math.min(length, parameters.friction() * pushed * weight);

        correctPosition(body, arm, null, null, direction.negate(), stop);
    }

    private void solveSelfCollision() {
        selfContacts.clear();

        Vector3d centerA = new Vector3d();
        Vector3d centerB = new Vector3d();

        for (int i = 0; i < bodies.length; i++) {
            Body a = bodies[i];

            for (int j = i + 1; j < bodies.length; j++) {
                Body b = bodies[j];

                double reach = a.selfBoundingRadius + b.selfBoundingRadius;

                if (a.position.distanceSquared(b.position) > reach * reach) continue;

                BitSet resting = restingOverlaps[i][j];

                for (int pa = 0; pa < a.selfCenters.length; pa++) {
                    for (int pb = 0; pb < b.selfCenters.length; pb++) {
                        a.rotation.transform(a.selfCenters[pa], centerA).add(a.position);
                        b.rotation.transform(b.selfCenters[pb], centerB).add(b.position);

                        Vector3d gap = new Vector3d(centerB).sub(centerA);
                        double distance = gap.length();
                        double overlap = a.selfRadii[pa] + b.selfRadii[pb] - distance;
                        int pair = pa * b.selfCenters.length + pb;

                        if (resting != null && resting.get(pair)) {
                            if (overlap <= 0.0D && !linked[i][j]) resting.clear(pair);

                            continue;
                        }

                        if (overlap <= SELF_CONTACT_SLOP || distance < 1.0E-9D) continue;

                        overlap -= SELF_CONTACT_SLOP;

                        Vector3d normal = gap.div(distance);
                        Vector3d armA = new Vector3d(centerA).fma(a.selfRadii[pa], normal).sub(a.position);
                        Vector3d armB = new Vector3d(centerB).fma(-b.selfRadii[pb], normal).sub(b.position);

                        correctPosition(a, armA, b, armB, normal, -overlap);
                        selfContacts.add(new SelfContact(
                            a, a.rotation.transform(a.selfCenters[pa], new Vector3d()).fma(a.selfRadii[pa], normal),
                            b, b.rotation.transform(b.selfCenters[pb], new Vector3d()).fma(-b.selfRadii[pb], normal),
                            normal
                        ));
                    }
                }
            }
        }
    }

    /// Moves the points at `armA` on `a` and `armB` on `b` to close `error` along the unit `direction`, which runs
    /// from `a`'s point toward `b`'s: `a` moves along it and `b` against it, each by its share. `b` null stands for
    /// the static level. Answers the Lagrange multiplier the correction took.
    private static double correctPosition(
        final Body a, final Vector3d armA, final @Nullable Body b, final @Nullable Vector3d armB, final Vector3d direction, final double error
    ) {
        double weightA = a.generalizedInverseMass(armA, direction);
        double weightB = b == null ? 0.0D : b.generalizedInverseMass(armB, direction);
        double weight = weightA + weightB;

        if (weight <= 0.0D) return 0.0D;

        double lambda = error / weight;
        Vector3d impulse = new Vector3d(direction).mul(lambda);

        a.position.fma(a.invMass, impulse);
        addRotation(a.rotation, a.applyInverseInertia(new Vector3d(armA).cross(impulse), new Vector3d()), 0.5D);

        if (b != null) {
            b.position.fma(-b.invMass, impulse);
            addRotation(b.rotation, b.applyInverseInertia(new Vector3d(armB).cross(impulse), new Vector3d()), -0.5D);
        }

        return lambda;
    }

    /// Turns `child` against `parent` by `angle` around the unit world `axis`, each by its share.
    private static void correctRotation(final Body parent, final Body child, final Vector3d axis, final double angle) {
        double weight = parent.angularInverseMass(axis) + child.angularInverseMass(axis);

        if (weight <= 0.0D) return;

        Vector3d impulse = new Vector3d(axis).mul(angle / weight);

        addRotation(child.rotation, child.applyInverseInertia(impulse, new Vector3d()), 0.5D);
        addRotation(parent.rotation, parent.applyInverseInertia(impulse, new Vector3d()), -0.5D);
    }

    /// `rotation` += `scale` · [`spin`, 0] · `rotation`, normalized.
    private static void addRotation(final Quaterniond rotation, final Vector3d spin, final double scale) {
        Quaterniond change = new Quaterniond(spin.x, spin.y, spin.z, 0.0D).mul(rotation);

        rotation.set(
            rotation.x + change.x * scale,
            rotation.y + change.y * scale,
            rotation.z + change.z * scale,
            rotation.w + change.w * scale
        ).normalize();
    }

    // ***************************************************************
    // Pose
    // ***************************************************************

    /// The pose that draws the bodies where they stand `partialTick` into the current tick, for an armature standing
    /// at `armatureToWorld`.
    public Pose makePose(final Transform armatureToWorld, final float partialTick) {
        Matrix4d[] world = new Matrix4d[bones.length];
        Vector3d position = new Vector3d();
        Quaterniond rotation = new Quaterniond();

        for (int i = 0; i < bones.length; i++) {
            Body body = bodyOfBone[i];

            if (body == null) continue;

            body.tickPosition.lerp(body.position, partialTick, position);
            body.tickRotation.slerp(body.rotation, partialTick, rotation);

            Vector3d head = rotation.transform(body.centerOfMass, new Vector3d()).negate().add(position);
            world[i] = new Matrix4d().translation(head).rotate(rotation).scale(body.boneScale);
        }

        Pose pose = new Pose();
        Matrix4d armatureToWorldMatrix = relativeMatrix(armatureToWorld, origin);

        for (int i = 0; i < bones.length; i++) {
            if (placements[i] == FOLLOW_PARENT) {
                world[i] = new Matrix4d(world[parents[i]]).mul(frozenOffsets[i]);
            } else if (placements[i] == FOLLOW_ANCHOR) {
                world[i] = new Matrix4d(world[anchor.boneIndex]).mul(frozenOffsets[i]);
            }

            Matrix4d parentSpace = parents[i] < 0 ? armatureToWorldMatrix : world[parents[i]];
            Matrix4d local = new Matrix4d(inverseLocals[i]).mul(new Matrix4d(parentSpace).invert()).mul(world[i]);

            pose.putBoneTransform(bones[i].getName(), Transform.fromMatrix(new Matrix4f(local)));
        }

        return pose;
    }

    // ***************************************************************
    // Bodies and joints
    // ***************************************************************

    /// One bone's rigid body. Public for the editor's mixins alone.
    @ApiStatus.Internal
    public static final class Body {
        /// Index of the bone this body carries, into [RagdollSimulation#bones].
        private final int boneIndex;

        private final double invMass;

        /// Inverse of the principal moments of inertia, in the bone's axes.
        private final Vector3d invInertia;

        /// The center of mass in the bone's axes, from the bone's head, in blocks.
        private final Vector3d centerOfMass;

        /// The scale of the bone's world transform, carried back onto it when the pose is made.
        private final Vector3d boneScale;

        private final double linearDamping;
        private final double angularDamping;

        /// The spheres standing for the colliders, centers in the bone's axes from [#centerOfMass], in blocks.
        private final Vector3d[] proxyCenters;
        private final double[] proxyRadii;

        /// Farthest a sphere of [#proxyCenters] reaches from the center of mass, in blocks.
        private final double boundingRadius;

        /// The spheres the body is pushed apart from the other bodies by, filling its colliders; laid out like
        /// [#proxyCenters].
        private final Vector3d[] selfCenters;
        private final double[] selfRadii;

        /// Farthest a sphere of [#selfCenters] reaches from the center of mass, in blocks.
        private final double selfBoundingRadius;

        private final Vector3d position = new Vector3d();
        private final Quaterniond rotation = new Quaterniond();
        private final Vector3d velocity = new Vector3d();

        /// World angular velocity, in radians per second.
        private final Vector3d angularVelocity = new Vector3d();

        /// Where the body was before the running substep.
        private final Vector3d previousPosition = new Vector3d();
        private final Quaterniond previousRotation = new Quaterniond();

        /// Where the body was at the start of the running tick.
        private final Vector3d tickPosition = new Vector3d();
        private final Quaterniond tickRotation = new Quaterniond();

        /// The block colliders the body may reach this tick, relative to the simulation's origin.
        private final List<AABB> terrain = new ArrayList<>();
        private AABB reach = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

        private Body(
            final int boneIndex, final double invMass, final Vector3d invInertia, final Vector3d centerOfMass, final Vector3d boneScale,
            final double linearDamping, final double angularDamping, final Vector3d[] proxyCenters, final double[] proxyRadii,
            final Vector3d[] selfCenters, final double[] selfRadii
        ) {
            this.boneIndex = boneIndex;
            this.invMass = invMass;
            this.invInertia = invInertia;
            this.centerOfMass = centerOfMass;
            this.boneScale = boneScale;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
            this.proxyCenters = proxyCenters;
            this.proxyRadii = proxyRadii;

            double bounding = 0.0D;

            for (int i = 0; i < proxyCenters.length; i++) bounding = Math.max(bounding, proxyCenters[i].length() + proxyRadii[i]);

            this.boundingRadius = bounding;
            this.selfCenters = selfCenters;
            this.selfRadii = selfRadii;

            double selfBounding = 0.0D;

            for (int i = 0; i < selfCenters.length; i++) selfBounding = Math.max(selfBounding, selfCenters[i].length() + selfRadii[i]);

            this.selfBoundingRadius = selfBounding;
        }

        /// The body of `authored` on the bone at `boneToWorld`: mass spread over the colliders by their volume, and
        /// the colliders sampled into spheres.
        private static Body build(final int boneIndex, final ArmaturePhysicsData.BoneBody authored, final Matrix4d boneToWorld) {
            Vector3d boneScale = boneToWorld.getScale(new Vector3d());
            double scale = (boneScale.x + boneScale.y + boneScale.z) / 3.0D;
            List<Vector3d> centers = new ArrayList<>();
            List<Double> radii = new ArrayList<>();
            List<Vector3d> selfCenters = new ArrayList<>();
            List<Double> selfRadii = new ArrayList<>();
            List<Shape> shapes = new ArrayList<>();

            for (Collider collider : authored.colliders()) {
                switch (collider) {
                    case SphereCollider sphere -> {
                        Vector3d center = scaled(sphere.getPivotTransform().loc(), scale);
                        double radius = sphere.getRadius() * scale;

                        centers.add(center);
                        radii.add(radius);
                        selfCenters.add(center);
                        selfRadii.add(radius);
                        shapes.add(new Shape(center, 4.0D / 3.0D * Math.PI * radius * radius * radius, new double[] {0.4D * radius * radius, 0.4D * radius * radius, 0.4D * radius * radius}));
                    }
                    case CapsuleCollider capsule -> {
                        Vector3d from = scaled(capsule.getLocalEndA(), scale);
                        Vector3d to = scaled(capsule.getLocalEndB(), scale);
                        double radius = capsule.getRadius() * scale;
                        double height = from.distance(to);
                        int samples = radius <= 0.0D ? 2 : Math.clamp((int) Math.ceil(height / radius) + 1, 2, MAX_CAPSULE_SPHERES);

                        for (int i = 0; i < samples; i++) {
                            Vector3d sample = from.lerp(to, i / (double) (samples - 1), new Vector3d());

                            centers.add(sample);
                            radii.add(radius);
                            selfCenters.add(sample);
                            selfRadii.add(radius);
                        }

                        Vector3d axis = height < 1.0E-9D ? new Vector3d(0.0D, 1.0D, 0.0D) : to.sub(from, new Vector3d()).div(height);
                        double length = height + radius * 2.0D;
                        double alongAxis = 0.5D * radius * radius;
                        double across = (3.0D * radius * radius + length * length) / 12.0D;
                        double[] moments = new double[3];

                        for (int k = 0; k < 3; k++) moments[k] = across + (alongAxis - across) * component(axis, k) * component(axis, k);

                        shapes.add(new Shape(
                            from.lerp(to, 0.5D, new Vector3d()),
                            Math.PI * radius * radius * height + 4.0D / 3.0D * Math.PI * radius * radius * radius,
                            moments
                        ));
                    }
                    case OrientedBoxCollider box -> {
                        Vector3d center = scaled(box.getPivotTransform().loc(), scale);
                        Vector3d half = scaled(box.getHalfExtents(), scale);
                        Vector3d[] axes = {scaled(box.getAxis(0), 1.0D), scaled(box.getAxis(1), 1.0D), scaled(box.getAxis(2), 1.0D)};
                        double[] lengths = {half.x * 2.0D, half.y * 2.0D, half.z * 2.0D};
                        double[] boxMoments = {
                            (lengths[1] * lengths[1] + lengths[2] * lengths[2]) / 12.0D,
                            (lengths[0] * lengths[0] + lengths[2] * lengths[2]) / 12.0D,
                            (lengths[0] * lengths[0] + lengths[1] * lengths[1]) / 12.0D
                        };
                        double[] moments = new double[3];

                        for (int k = 0; k < 3; k++) {
                            for (int j = 0; j < 3; j++) moments[k] += boxMoments[j] * component(axes[j], k) * component(axes[j], k);
                        }

                        for (int corner = 0; corner < 8; corner++) {
                            centers.add(new Vector3d(center)
                                .fma((corner & 1) == 0 ? -half.x : half.x, axes[0])
                                .fma((corner & 2) == 0 ? -half.y : half.y, axes[1])
                                .fma((corner & 4) == 0 ? -half.z : half.z, axes[2]));
                            radii.add(CORNER_RADIUS);
                        }

                        double inner = Math.min(half.x, Math.min(half.y, half.z));

                        centers.add(center);
                        radii.add(inner);

                        // Spheres as thick as the box's thinnest side, laid on a grid along the other two
                        int[] counts = new int[3];
                        double[] halves = {half.x, half.y, half.z};

                        for (int k = 0; k < 3; k++) {
                            counts[k] = inner <= 0.0D ? 1 : Math.clamp((int) Math.ceil((halves[k] - inner) * 2.0D / inner) + 1, 1, MAX_BOX_SPHERES_PER_AXIS);
                        }

                        for (int ix = 0; ix < counts[0]; ix++) {
                            for (int iy = 0; iy < counts[1]; iy++) {
                                for (int iz = 0; iz < counts[2]; iz++) {
                                    selfCenters.add(new Vector3d(center)
                                        .fma(gridOffset(ix, counts[0], halves[0] - inner), axes[0])
                                        .fma(gridOffset(iy, counts[1], halves[1] - inner), axes[1])
                                        .fma(gridOffset(iz, counts[2], halves[2] - inner), axes[2]));
                                    selfRadii.add(inner);
                                }
                            }
                        }

                        shapes.add(new Shape(center, lengths[0] * lengths[1] * lengths[2], moments));
                    }
                    default -> {}
                }
            }

            double totalVolume = 0.0D;

            for (Shape shape : shapes) totalVolume += shape.volume;

            Vector3d centerOfMass = new Vector3d();

            for (Shape shape : shapes) {
                double share = totalVolume > 0.0D ? shape.volume / totalVolume : 1.0D / shapes.size();
                centerOfMass.fma(share, shape.center);
            }

            double mass = authored.mass();
            double[] inertia = new double[3];

            for (Shape shape : shapes) {
                double shapeMass = mass * (totalVolume > 0.0D ? shape.volume / totalVolume : 1.0D / shapes.size());
                Vector3d offset = new Vector3d(shape.center).sub(centerOfMass);
                double offsetSquared = offset.lengthSquared();

                for (int k = 0; k < 3; k++) {
                    inertia[k] += shapeMass * (shape.moments[k] + offsetSquared - component(offset, k) * component(offset, k));
                }
            }

            Vector3d[] proxyCenters = new Vector3d[centers.size()];
            double[] proxyRadii = new double[radii.size()];

            for (int i = 0; i < proxyCenters.length; i++) {
                proxyCenters[i] = centers.get(i).sub(centerOfMass, new Vector3d());
                proxyRadii[i] = radii.get(i);
            }

            Vector3d[] selfCenterArray = new Vector3d[selfCenters.size()];
            double[] selfRadiusArray = new double[selfRadii.size()];

            for (int i = 0; i < selfCenterArray.length; i++) {
                selfCenterArray[i] = selfCenters.get(i).sub(centerOfMass, new Vector3d());
                selfRadiusArray[i] = selfRadii.get(i);
            }

            Body body = new Body(
                boneIndex, 1.0D / mass,
                new Vector3d(1.0D / Math.max(inertia[0], 1.0E-6D), 1.0D / Math.max(inertia[1], 1.0E-6D), 1.0D / Math.max(inertia[2], 1.0E-6D)),
                centerOfMass, boneScale, authored.linearDamping(), authored.angularDamping(), proxyCenters, proxyRadii,
                selfCenterArray, selfRadiusArray
            );

            boneToWorld.getNormalizedRotation(body.rotation);
            body.position.set(body.rotation.transform(centerOfMass, new Vector3d()).add(boneToWorld.getTranslation(new Vector3d())));
            body.tickPosition.set(body.position);
            body.tickRotation.set(body.rotation);

            return body;
        }

        /// Takes the velocity of the bone moving from `before` to `now` in `elapsed` seconds, times `scale`.
        private void takeVelocity(final Matrix4d before, final Matrix4d now, final double elapsed, final float scale) {
            Quaterniond rotationBefore = before.getNormalizedRotation(new Quaterniond());
            Vector3d positionBefore = rotationBefore.transform(centerOfMass, new Vector3d()).add(before.getTranslation(new Vector3d()));

            velocity.set(position).sub(positionBefore).mul(scale / elapsed);

            Quaterniond turn = rotation.mul(rotationBefore.conjugate(), new Quaterniond());

            if (turn.w < 0.0D) turn.set(-turn.x, -turn.y, -turn.z, -turn.w);

            double sine = Math.sqrt(turn.x * turn.x + turn.y * turn.y + turn.z * turn.z);

            if (sine > 1.0E-9D) {
                angularVelocity.set(turn.x, turn.y, turn.z).mul(2.0D * Math.atan2(sine, turn.w) / sine * scale / elapsed);
            }
        }

        /// The pairs of this body's and `other`'s self-collision spheres that overlap where they stand, bit
        /// `i * other's sphere count + j`.
        private BitSet overlappingSelfSpheres(final Body other) {
            BitSet overlapping = new BitSet();
            Vector3d a = new Vector3d();
            Vector3d b = new Vector3d();

            for (int i = 0; i < selfCenters.length; i++) {
                rotation.transform(selfCenters[i], a).add(position);

                for (int j = 0; j < other.selfCenters.length; j++) {
                    other.rotation.transform(other.selfCenters[j], b).add(other.position);

                    double reach = selfRadii[i] + other.selfRadii[j];

                    if (a.distanceSquared(b) < reach * reach) overlapping.set(i * other.selfCenters.length + j);
                }
            }

            return overlapping;
        }

        /// The pairs of this body's and `other`'s self-collision spheres where either sphere reaches within the two
        /// radii of `point`, bit `i * other's sphere count + j`.
        private BitSet selfSpheresNearPoint(final Body other, final Vector3d point) {
            BitSet near = new BitSet();
            Vector3d a = new Vector3d();
            Vector3d b = new Vector3d();

            for (int i = 0; i < selfCenters.length; i++) {
                rotation.transform(selfCenters[i], a).add(position);

                for (int j = 0; j < other.selfCenters.length; j++) {
                    other.rotation.transform(other.selfCenters[j], b).add(other.position);

                    double reach = selfRadii[i] + other.selfRadii[j];

                    if (a.distance(point) < reach || b.distance(point) < reach) near.set(i * other.selfCenters.length + j);
                }
            }

            return near;
        }

        /// The offset of sample `index` of `count` laid evenly over [-`extent`, `extent`].
        private static double gridOffset(final int index, final int count, final double extent) {
            return count <= 1 ? 0.0D : Mth.lerp(index / (double) (count - 1), -extent, extent);
        }

        /// Inverse inertia in world space, applied to `vector`, into `dest`.
        private Vector3d applyInverseInertia(final Vector3d vector, final Vector3d dest) {
            rotation.transformInverse(vector, dest);
            dest.mul(invInertia);

            return rotation.transform(dest);
        }

        /// How easily the point at `arm` moves along the unit `direction`.
        private double generalizedInverseMass(final Vector3d arm, final Vector3d direction) {
            Vector3d torque = new Vector3d(arm).cross(direction);

            return invMass + torque.dot(applyInverseInertia(torque, new Vector3d()));
        }

        /// How easily the body turns around the unit world `axis`.
        private double angularInverseMass(final Vector3d axis) {
            return axis.dot(applyInverseInertia(axis, new Vector3d()));
        }

        private static Vector3d scaled(final Vec3 vector, final double scale) {
            return new Vector3d(vector.x * scale, vector.y * scale, vector.z * scale);
        }

        private static double component(final Vector3d vector, final int axis) {
            return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
        }
    }

    /// One collider's share of a body: its center, its volume, and its moments of inertia per unit mass in the
    /// bone's axes.
    private record Shape(Vector3d center, double volume, double[] moments) {}

    /// The link of `child`'s bone head to `parent`.
    ///
    /// @param parentAnchor the bone head in `parent`'s axes, from its center of mass
    /// @param childAnchor  the bone head in `child`'s axes, from its center of mass
    /// @param frame        the rotation `child` has at its rest pose, in `parent`'s axes
    /// @param constraint   the limits `child` turns within, null for none
    private record Joint(Body parent, Body child, Vector3d parentAnchor, Vector3d childAnchor, Quaterniond frame,
                         ArmaturePhysicsData.@Nullable BoneConstraint constraint) {
        /// The joint of `child`, whose bone stands at `childToWorld` and would stand at `restToWorld` with no pose of
        /// its own.
        private static Joint build(
            final Body parent, final Body child, final Matrix4d childToWorld, final Matrix4d restToWorld,
            final ArmaturePhysicsData.@Nullable BoneConstraint constraint
        ) {
            Vector3d head = childToWorld.getTranslation(new Vector3d());
            Vector3d parentAnchor = parent.rotation.transformInverse(new Vector3d(head).sub(parent.position));
            Vector3d childAnchor = child.rotation.transformInverse(new Vector3d(head).sub(child.position));
            Quaterniond frame = parent.rotation.conjugate(new Quaterniond()).mul(restToWorld.getNormalizedRotation(new Quaterniond()));

            return new Joint(parent, child, parentAnchor, childAnchor, frame, constraint);
        }
    }

    /// A terrain contact of the running substep, kept for the velocity pass.
    ///
    /// @param arm           from the body's center of mass to the contact point, in world axes
    /// @param normal        out of the terrain
    /// @param approachSpeed the point's speed along `normal` before the contact was solved
    private record Contact(Body body, Vector3d arm, Vector3d normal, double approachSpeed) {}

    /// A contact between two bodies in the running substep, kept for the velocity pass.
    ///
    /// @param armA   from `a`'s center of mass to its contact point, in world axes
    /// @param armB   from `b`'s center of mass to its contact point, in world axes
    /// @param normal from `a` toward `b`
    private record SelfContact(Body a, Vector3d armA, Body b, Vector3d armB, Vector3d normal) {}
}
