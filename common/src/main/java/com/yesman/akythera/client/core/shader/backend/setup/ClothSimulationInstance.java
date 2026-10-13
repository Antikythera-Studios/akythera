package com.yesman.akythera.client.core.shader.backend.setup;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.core.asset.physics.ClothSimulationParameters;
import com.yesman.akythera.client.core.physics.cloth.ClothColliderPacker;
import com.yesman.akythera.client.core.physics.cloth.ClothTopology;
import com.yesman.akythera.client.core.shader.backend.ComputeBackend;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.program.ComputeProgram;
import com.yesman.akythera.client.core.shader.backend.program.ComputeUniform;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.physics.collider.Collider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/// One simulated cloth: the particle state of a [ClothComputeShader], stepped once per rendered frame.
///
/// Particles are simulated at the size the cloth was authored in. The scale of the pose it is drawn
/// under is applied to the simulated result, as it is to any other vertex.
///
/// State never leaves the GPU. The first frame, a reset, a frame gap past [#RESET_GAP_SECONDS] and an
/// owner farther than [#REBASE_DISTANCE] from the simulation origin all re-skin every particle onto its
/// animated position.
public final class ClothSimulationInstance implements AutoCloseable {
    /// Neighbors a particle keeps for self-collision. Matches `MAX_NEIGHBORS` in the solver and hash
    /// sources.
    public static final int MAX_NEIGHBORS = 16;

    /// position, previous, target, previous target, delta, axis x, axis y
    private static final int STATE_STRIDE = 112;
    private static final int KEY_STRIDE = 8;
    private static final int CELL_STRIDE = 8;
    private static final int PARAMETERS_BYTES = 224;
    private static final int WORD_BYTES = 4;

    /// Downward acceleration at gravity scale 1, blocks/s²
    private static final float GRAVITY = 9.81F;

    private static final float MAX_FRAME_SECONDS = 1.0F / 20.0F;
    private static final float RESET_FRAME_SECONDS = 1.0F / 60.0F;
    private static final double RESET_GAP_SECONDS = 0.25D;

    /// Distance from the simulation origin past which the owner is re-anchored, in blocks
    private static final double REBASE_DISTANCE = 32.0D;

    /// Neighbor search radius as a multiple of the self-collision thickness
    private static final float NEIGHBOR_DISTANCE_SCALE = 4.0F;

    // Solver stages, `stage` in cloth_solver.comp
    private static final int STAGE_INTEGRATE = 0;
    private static final int STAGE_DISTANCE = 1;
    private static final int STAGE_SELF_COLLIDE = 2;
    private static final int STAGE_APPLY_DELTA = 3;
    private static final int STAGE_CONSTRAIN = 4;
    private static final int STAGE_FRAMES = 5;
    private static final int STAGE_FOLLOW = 6;

    // Hash stages, `stage` in cloth_spatial_hash.comp
    private static final int STAGE_KEYS = 0;
    private static final int STAGE_SORT = 1;
    private static final int STAGE_CLEAR_CELLS = 2;
    private static final int STAGE_BUILD_CELLS = 3;
    private static final int STAGE_NEIGHBORS = 4;

    private final ClothComputeShader setup;
    private final int particleCount;
    private final int offsetCount;
    private final int keyCount;
    private final int tableSize;

    private final GpuBuffer state;
    private final GpuBuffer neighbors;

    /// Allocated the first frame self-collision is on
    private @Nullable GpuBuffer keys;
    private @Nullable GpuBuffer cells;

    /// World position the simulation space is centered on
    private Vec3 origin = Vec3.ZERO;

    private final Matrix4f previousArmatureToSimulation = new Matrix4f();
    private boolean initialized;
    private boolean resetRequested;
    private long lastStepNanos;
    private int lastFrame = Integer.MIN_VALUE;

    ClothSimulationInstance(final ClothComputeShader setup) {
        this.setup = setup;
        this.particleCount = setup.getTopology().particleCount();
        this.offsetCount = setup.getTopology().offsetCount();
        this.keyCount = Mth.smallestEncompassingPowerOfTwo(Math.max(1, particleCount));
        this.tableSize = Mth.smallestEncompassingPowerOfTwo(Math.max(1, particleCount * 2));

        ComputeBackend backend = ComputeBackends.get();

        state = backend.createComputeStorageBuffer(
            () -> Akythera.prefix("cloth_state"), (long) STATE_STRIDE * Math.max(1, particleCount + offsetCount)
        );
        neighbors = backend.createComputeStorageBuffer(
            () -> Akythera.prefix("cloth_neighbors"), (long) WORD_BYTES * (MAX_NEIGHBORS + 1) * Math.max(1, particleCount)
        );
    }

    /// Everything one [#step] reads that [#prepareFrame] wrote.
    ///
    /// @param parameters         the `ParamsBuffer` slice, null when [#simulate] is false
    /// @param colliders          the `ColliderBuffer` slice, null when [#simulate] is false
    /// @param simulationToCamera simulation space to the camera-relative space the pose stack is in,
    ///                           carrying the pose's scale
    /// @param simulate           false for a second submission within one frame
    public record Frame(
        @Nullable GpuBufferSlice parameters,
        @Nullable GpuBufferSlice colliders,
        Matrix4f simulationToCamera,
        int substeps,
        boolean selfCollision,
        boolean simulate
    ) {
    }

    /// Re-skins every particle on the next simulated frame.
    public void requestReset() {
        resetRequested = true;
    }

    public ClothComputeShader getSetup() {
        return setup;
    }

    /// The renderer frame this instance was last prepared in.
    public int getLastFrame() {
        return lastFrame;
    }

    GpuBuffer state() {
        return state;
    }

    /// Writes this frame's parameters and colliders into `storage`.
    ///
    /// @param armatureToCamera       the armature-space pose, camera-relative (`PoseStack.Pose#pose`)
    /// @param cameraPosition         world position of the camera; [Vec3#ZERO] in a model viewer
    /// @param simulationRotation     a rotation the simulation frame carries and the draw undoes, about
    ///                               the instance origin: identity in the world, the camera's yaw in a
    ///                               model viewer, where turning the frame is how the user turns the
    ///                               model
    /// @param forwardBoundTransforms bone model transforms of the current animation, before
    ///                               [Armature#boundBackwardTransform]
    public Frame prepareFrame(
        final DynamicStorageBuffer storage,
        final Matrix4f armatureToCamera,
        final Vec3 cameraPosition,
        final Matrix4f simulationRotation,
        final Matrix4f[] forwardBoundTransforms,
        final Armature armature,
        final @Nullable ArmaturePhysicsData armaturePhysics,
        final ClothSimulationParameters parameters,
        final int frameIndex
    ) {
        boolean simulate = frameIndex != lastFrame;
        lastFrame = frameIndex;

        long now = System.nanoTime();
        double elapsed = (now - lastStepNanos) / 1.0E9D;
        Vector3f armatureOrigin = armatureToCamera.getTranslation(new Vector3f());
        Vec3 armatureWorld = cameraPosition.add(armatureOrigin.x, armatureOrigin.y, armatureOrigin.z);
        boolean reset = !initialized || resetRequested || elapsed > RESET_GAP_SECONDS
            || armatureWorld.distanceTo(origin) > REBASE_DISTANCE;

        if (simulate && reset) {
            origin = armatureWorld;
        }

        // The pose with its scale taken off: where the armature is and how it is turned
        Matrix4f rigidToCamera = new Matrix4f().translationRotate(
            armatureOrigin.x, armatureOrigin.y, armatureOrigin.z,
            armatureToCamera.getUnnormalizedRotation(new Quaternionf()).normalize()
        );

        // R · T · rigid on the way in, pose · rigid⁻¹ · T⁻¹ · R⁻¹ on the way out: the two compose back to
        // the pose, so a particle resting on its animated position draws where the skinned vertex would
        Matrix4f armatureToSimulation = new Matrix4f(simulationRotation)
            .translate((float) (cameraPosition.x - origin.x), (float) (cameraPosition.y - origin.y), (float) (cameraPosition.z - origin.z))
            .mul(rigidToCamera);
        Matrix4f simulationToCamera = new Matrix4f(armatureToCamera)
            .mul(rigidToCamera.invert(new Matrix4f()))
            .translate((float) (origin.x - cameraPosition.x), (float) (origin.y - cameraPosition.y), (float) (origin.z - cameraPosition.z))
            .mul(new Matrix4f(simulationRotation).invert());

        if (!simulate) {
            return new Frame(null, null, simulationToCamera, 0, false, false);
        }

        int substeps = parameters.substeps();
        float substepSeconds = (reset ? RESET_FRAME_SECONDS : (float) Math.min(elapsed, MAX_FRAME_SECONDS)) / substeps;
        Matrix4f correction = new Matrix4f();

        if (!reset) {
            // The owner's rigid motion since last frame, carried onto the particles by (1 - inertia scale)
            Matrix4f delta = new Matrix4f(armatureToSimulation).mul(new Matrix4f(previousArmatureToSimulation).invert());
            Vector3f pivot = previousArmatureToSimulation.getTranslation(new Vector3f());
            Vector3f translation = delta.transformPosition(new Vector3f(pivot)).sub(pivot);
            Quaternionf rotation = delta.getUnnormalizedRotation(new Quaternionf()).normalize();
            float linearCarry = 1.0F - parameters.linearInertiaScale();
            float angularCarry = 1.0F - parameters.angularInertiaScale();

            correction
                .translation(pivot.x + translation.x * linearCarry, pivot.y + translation.y * linearCarry, pivot.z + translation.z * linearCarry)
                .rotate(new Quaternionf().slerp(rotation, angularCarry))
                .translate(-pivot.x, -pivot.y, -pivot.z);
        }

        boolean selfCollision = parameters.selfCollision() && particleCount > 1;

        if (selfCollision && keys == null) {
            ComputeBackend backend = ComputeBackends.get();

            keys = backend.createComputeStorageBuffer(() -> Akythera.prefix("cloth_hash_keys"), (long) KEY_STRIDE * keyCount);
            cells = backend.createComputeStorageBuffer(() -> Akythera.prefix("cloth_hash_cells"), (long) CELL_STRIDE * tableSize);
        }

        ClientComponentsFactory factory = ClientComponentsFactory.getImpl();
        int colliderCount = 0;

        if (armaturePhysics != null) {
            for (Map.Entry<String, ArmaturePhysicsData.BoneBody> body : armaturePhysics.bodies().entrySet()) {
                if (armature.getBoneByName(body.getKey()) == null) continue;

                for (Collider collider : body.getValue().colliders()) {
                    if (factory.getClothColliderPacker(collider) != null) colliderCount++;
                }
            }
        }

        GpuBufferSlice colliders = storage.write(Math.max(1, colliderCount) * ClothColliderPacker.STRIDE, buffer -> {
            if (armaturePhysics == null) return;

            Matrix4f colliderToSimulation = new Matrix4f();
            int offset = 0;

            for (Map.Entry<String, ArmaturePhysicsData.BoneBody> body : armaturePhysics.bodies().entrySet()) {
                Bone bone = armature.getBoneByName(body.getKey());

                if (bone == null) continue;

                armatureToSimulation.mul(forwardBoundTransforms[bone.getId()], colliderToSimulation);

                for (Collider collider : body.getValue().colliders()) {
                    ClothColliderPacker<Collider> packer = factory.getClothColliderPacker(collider);

                    if (packer == null) continue;

                    packer.pack(collider, colliderToSimulation, buffer, offset);
                    offset += ClothColliderPacker.STRIDE;
                }
            }
        });

        int packedColliders = colliderCount;
        float neighborDistance = parameters.selfCollisionThickness() * NEIGHBOR_DISTANCE_SCALE;

        GpuBufferSlice parameterSlice = storage.write(PARAMETERS_BYTES, buffer -> {
            armatureToSimulation.get(0, buffer);
            correction.get(64, buffer);

            buffer.putFloat(128, 0.0F);
            buffer.putFloat(132, -GRAVITY * parameters.gravityScale());
            buffer.putFloat(136, 0.0F);
            buffer.putFloat(140, substepSeconds);

            buffer.putFloat(144, parameters.stretchCompliance());
            buffer.putFloat(148, parameters.bendCompliance());
            buffer.putFloat(152, parameters.damping());
            buffer.putFloat(156, parameters.friction());

            buffer.putFloat(160, parameters.maxDistance());
            buffer.putFloat(164, parameters.tetherScale());
            buffer.putFloat(168, parameters.collisionThickness());
            buffer.putFloat(172, parameters.selfCollisionThickness());

            buffer.putFloat(176, neighborDistance);
            buffer.putFloat(180, neighborDistance);
            buffer.putFloat(184, 0.0F);
            buffer.putFloat(188, 0.0F);

            buffer.putInt(192, particleCount);
            buffer.putInt(196, packedColliders);
            buffer.putInt(200, substeps);
            buffer.putInt(204, reset ? 1 : 0);
            buffer.putInt(208, keyCount);
            buffer.putInt(212, tableSize);
            buffer.putInt(216, offsetCount);
            buffer.putInt(220, 0);
        });

        previousArmatureToSimulation.set(armatureToSimulation);
        lastStepNanos = now;
        initialized = true;
        resetRequested = false;

        return new Frame(parameterSlice, colliders, simulationToCamera, substeps, selfCollision, true);
    }

    /// Runs one frame of the solver over [#state]. `poses` is the fully bound skinning slice.
    public void step(final Frame frame, final GpuBufferSlice poses) {
        if (!frame.simulate() || particleCount == 0) return;

        GpuBufferSlice parameters = frame.parameters();
        GpuBufferSlice colliders = frame.colliders();
        int particleGroups = groups(particleCount);

        try (ComputeProgram.Dispatch dispatch = ClothProgram.FRAME.get().begin()) {
            dispatch.bindStorageBuffer(0, parameters)
                .bindStorageBuffer(1, poses)
                .bindStorageBuffer(2, setup.boneWeights())
                .bindStorageBuffer(3, setup.particles())
                .bindStorageBuffer(4, state)
                .dispatch(groups(particleCount + offsetCount), 1, 1);
        }

        if (frame.selfCollision() && keys != null && cells != null) {
            ComputeProgram hash = ClothProgram.SPATIAL_HASH.get();
            ComputeUniform stage = hash.uniform("stage");
            ComputeUniform sortK = hash.uniform("sort_k");
            ComputeUniform sortJ = hash.uniform("sort_j");
            int keyGroups = groups(keyCount);

            try (ComputeProgram.Dispatch dispatch = hash.begin()) {
                dispatch.bindStorageBuffer(0, parameters)
                    .bindStorageBuffer(1, setup.particles())
                    .bindStorageBuffer(2, state)
                    .bindStorageBuffer(3, keys)
                    .bindStorageBuffer(4, cells)
                    .bindStorageBuffer(5, neighbors);

                stage.putUnsignedInt(STAGE_KEYS);
                dispatch.dispatch(keyGroups, 1, 1);

                stage.putUnsignedInt(STAGE_SORT);

                for (int k = 2; k <= keyCount; k <<= 1) {
                    for (int j = k >> 1; j > 0; j >>= 1) {
                        sortK.putUnsignedInt(k);
                        sortJ.putUnsignedInt(j);
                        dispatch.dispatch(keyGroups, 1, 1);
                    }
                }

                stage.putUnsignedInt(STAGE_CLEAR_CELLS);
                dispatch.dispatch(groups(tableSize), 1, 1);

                stage.putUnsignedInt(STAGE_BUILD_CELLS);
                dispatch.dispatch(particleGroups, 1, 1);

                stage.putUnsignedInt(STAGE_NEIGHBORS);
                dispatch.dispatch(particleGroups, 1, 1);
            }
        }

        ComputeProgram solver = ClothProgram.SOLVER.get();
        ComputeUniform stage = solver.uniform("stage");
        ComputeUniform rangeOffset = solver.uniform("range_offset");
        ComputeUniform rangeCount = solver.uniform("range_count");
        ComputeUniform substep = solver.uniform("substep");
        ClothTopology topology = setup.getTopology();

        try (ComputeProgram.Dispatch dispatch = solver.begin()) {
            dispatch.bindStorageBuffer(0, parameters)
                .bindStorageBuffer(1, setup.particles())
                .bindStorageBuffer(2, setup.constraints())
                .bindStorageBuffer(3, colliders)
                .bindStorageBuffer(4, state)
                .bindStorageBuffer(5, neighbors)
                .bindStorageBuffer(6, setup.triangles());

            for (int s = 0; s < frame.substeps(); s++) {
                substep.putUnsignedInt(s);

                stage.putUnsignedInt(STAGE_INTEGRATE);
                dispatch.dispatch(particleGroups, 1, 1);

                stage.putUnsignedInt(STAGE_DISTANCE);

                for (int color = 0; color < topology.colorCounts().length; color++) {
                    int count = topology.colorCounts()[color];

                    if (count == 0) continue;

                    rangeOffset.putUnsignedInt(topology.colorOffsets()[color]);
                    rangeCount.putUnsignedInt(count);
                    dispatch.dispatch(groups(count), 1, 1);
                }

                if (frame.selfCollision()) {
                    stage.putUnsignedInt(STAGE_SELF_COLLIDE);
                    dispatch.dispatch(particleGroups, 1, 1);

                    stage.putUnsignedInt(STAGE_APPLY_DELTA);
                    dispatch.dispatch(particleGroups, 1, 1);
                }

                stage.putUnsignedInt(STAGE_CONSTRAIN);
                dispatch.dispatch(particleGroups, 1, 1);
            }

            stage.putUnsignedInt(STAGE_FRAMES);
            dispatch.dispatch(particleGroups, 1, 1);

            if (offsetCount > 0) {
                stage.putUnsignedInt(STAGE_FOLLOW);
                dispatch.dispatch(groups(offsetCount), 1, 1);
            }
        }
    }

    private static int groups(final int invocations) {
        return (invocations + AnimationComputeShader.WORK_GROUP_SIZE - 1) / AnimationComputeShader.WORK_GROUP_SIZE;
    }

    @Override
    public void close() {
        state.close();
        neighbors.close();

        if (keys != null) keys.close();
        if (cells != null) cells.close();
    }
}
