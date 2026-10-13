package com.yesman.akythera.client.core.renderer;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.physics.ClothSimulationParameters;
import com.yesman.akythera.client.core.shader.backend.setup.AnimationComputeShader;
import com.yesman.akythera.client.core.shader.backend.setup.ClothComputeShader;
import com.yesman.akythera.client.core.shader.backend.setup.ClothSimulationInstance;
import com.yesman.akythera.client.core.shader.backend.setup.DynamicStorageBuffer;
import com.yesman.akythera.client.core.shader.backend.setup.SkinnedDrawContext;
import com.yesman.akythera.client.core.shader.backend.setup.TriangleDepthSort;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Draws what a patch renders: flattens its component tree into the [RenderableComponent]s under it
/// and runs them ([#collect], [#extract], [#submit]), then draws every skinned mesh those submitted
/// ([#submitSkinnedMesh]).
///
/// The two halves run in different phases. The tree walk is stateless and runs inside
/// `LivingEntityRenderer`; the skinned draws queue up there and flush later, when
/// `FeatureRenderDispatcher.PreparedFrame` drives [#renderSolid], [#renderTranslucent] and
/// [#clear]. [#endFrame] runs on the frame boundary instead, which is the latency the ring behind
/// [#frameStorage] is sized for.
///
/// [#drawSkinnedMeshImmediate] is the way in for callers that reach neither phase.
public final class PatchModelRenderer {
    // 16(elements) x 4(bytes)
    private static final int MATRIX_BYTES = 64;
    // Up to 32 part visibility flags are available
    private static final int FLAG_WORD_BYTES = 4;

    /// `DrawParamsBuffer` header of `vanilla_cloth_transformer.comp`: three matrices and four words
    private static final int CLOTH_DRAW_HEADER_BYTES = 208;

    /// Frames an in-game cloth instance survives without a submit before its GPU state is released
    private static final int STALE_FRAMES = 200;

    /// Where the camera is for geometry placed relative to it, as the level's is.
    private static final Vector3fc CAMERA = new Vector3f();

    /// What an entity's cloth simulates in: the camera's own frame, unturned.
    private static final Matrix4f NO_SIMULATION_ROTATION = new Matrix4f();

    private static final PatchModelRenderer INSTANCE = new PatchModelRenderer();

    public static PatchModelRenderer getInstance() {
        return INSTANCE;
    }

    /// Every switched-on renderable under `host`, parents before children, each paired with the host
    /// it is attached to and the [TransformComponent] that places it. `space` is the one `host` itself
    /// lives in—null at the patch root.
    public static void collect(
        final ComponentAttachable host,
        final @Nullable TransformComponent space,
        final List<RenderableSubmit> out
    ) {
        for (PatchComponent component : host.getComponents().values()) {
            if (!component.isEnabled()) continue;

            TransformComponent placing = component instanceof TransformComponent transform ? transform : space;

            if (component instanceof RenderableComponent<?> renderable) {
                out.add(new RenderableSubmit(renderable, host, placing));
            }

            if (component instanceof ComponentAttachable attachable) {
                collect(attachable, placing, out);
            }
        }
    }

    /// Runs [RenderableComponent#extract] on every collected renderable the state fits.
    @SuppressWarnings("unchecked")
    public static void extract(
        final List<RenderableSubmit> submits,
        final EntityPatch<?> patch,
        final LivingEntityRenderState state,
        final float partialTick
    ) {
        for (RenderableSubmit submit : submits) {
            RenderableComponent<?> component = submit.component();

            if (component.stateType().isInstance(state)) {
                ((RenderableComponent<LivingEntityRenderState>)component).extract(patch, state, partialTick);
            }
        }
    }

    /// Submits every collected renderable the state fits, each in armature space under its placing transform.
    @SuppressWarnings("unchecked")
    public static void submit(
        final List<RenderableSubmit> submits,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state
    ) {
        for (RenderableSubmit submit : submits) {
            RenderableComponent<?> component = submit.component();

            if (!component.stateType().isInstance(state)) continue;

            poseStack.pushPose();
            RenderUtils.toArmatureSpace(poseStack);

            if (submit.space() != null) poseStack.mulPose(submit.space().modelTransform().toMatrix());

            ((RenderableComponent<LivingEntityRenderState>) component)
                .submit(poseStack, submitNodeCollector, lightCoords, state, state.yRot, state.xRot);

            poseStack.popPose();
        }
    }

    private final Map<RenderType, List<SkinnedSubmit>> solidSubmits = new LinkedHashMap<>();
    private final Map<RenderType, List<SkinnedSubmit>> translucentSubmits = new LinkedHashMap<>();

    /// Scratch the bound skeleton is resolved into. Consumed within [#buildContext] before the next
    /// call, so one array serves every entity.
    private Matrix4f[] boundTransforms = Matrix4fUtils.allocateMatrixArray(0);

    /// Permanent communication channel buffer to GPU
    private @Nullable DynamicStorageBuffer frameStorage;

    /// The simulated state of every in-game cloth, by the identity of the object that submits it
    private final Map<Object, ClothSimulationInstance> clothInstances = new IdentityHashMap<>();

    /// Counts [#endFrame] calls
    private int frameIndex;

    private PatchModelRenderer() {
    }

    /// Uploads this entity's pose and part visibility and queues its draw.
    public void submitSkinnedMesh(
        final SkeletalMesh mesh,
        final AssetAccessor<? extends Armature> armature,
        final Pose animationPose,
        final RenderType renderType,
        final PoseStack.Pose pose,
        final int packedLight,
        final int packedOverlay,
        final int color,
        final int outlineColor
    ) {
        AnimationComputeShader setup = mesh.getAnimationComputeShader();
        if (setup == null) return;

        SkinnedDrawContext context = buildContext(mesh, armature, animationPose, pose, packedLight, packedOverlay, color, outlineColor);
        SkinnedSubmit submit = new SkinnedSubmit(setup, () -> setup.skin(context), outlineColor);

        Map<RenderType, List<SkinnedSubmit>> bucket = renderType.hasBlending() ? translucentSubmits : solidSubmits;
        bucket.computeIfAbsent(renderType, _ -> new ArrayList<>()).add(submit);
    }

    /// Queues a simulated cloth draw for `owner`, creating or replacing its instance when `setup`
    /// changed. `pose` is in armature space; `cameraPosition` is the world camera position.
    public void submitClothMesh(
        final Object owner,
        final ClothComputeShader setup,
        final AssetAccessor<? extends Armature> armature,
        final Pose animationPose,
        final @Nullable ArmaturePhysicsData armaturePhysics,
        final ClothSimulationParameters parameters,
        final RenderType renderType,
        final PoseStack.Pose pose,
        final Vec3 cameraPosition,
        final int packedLight,
        final int packedOverlay,
        final int color,
        final int outlineColor
    ) {
        ClothSimulationInstance instance = clothInstances.get(owner);

        if (instance == null || instance.getSetup() != setup) {
            if (instance != null) instance.close();

            instance = setup.createInstance();
            clothInstances.put(owner, instance);
        }

        SkinnedSubmit submit = new SkinnedSubmit(
            setup.getMeshShader(),
            prepareCloth(instance, armature, animationPose, armaturePhysics, parameters, pose, cameraPosition, NO_SIMULATION_ROTATION, frameIndex, packedLight, packedOverlay, color, outlineColor),
            outlineColor
        );

        Map<RenderType, List<SkinnedSubmit>> bucket = renderType.hasBlending() ? translucentSubmits : solidSubmits;
        bucket.computeIfAbsent(renderType, _ -> new ArrayList<>()).add(submit);
    }

    /// Skins `mesh` and draws it on the spot instead of queueing it, for callers outside the
    /// feature-render phase: the model previewer runs while the GUI is extracted and has redirected
    /// the output overrides onto a render target of its own. Draws no outline.
    public void drawSkinnedMeshImmediate(
        final SkeletalMesh mesh,
        final AssetAccessor<? extends Armature> armature,
        final Pose animationPose,
        final RenderType renderType,
        final PoseStack.Pose pose,
        final int packedLight,
        final int packedOverlay,
        final int color,
        final Vector3fc eye
    ) {
        AnimationComputeShader setup = mesh.getAnimationComputeShader();
        if (setup == null) return;

        setup.skin(buildContext(mesh, armature, animationPose, pose, packedLight, packedOverlay, color, 0));
        drawSkinned(renderType, setup, eye);
    }

    /// Simulates `instance` and draws it on the spot, for a model viewer that owns its instance.
    /// `frame` is the caller's own frame counter; the viewer's space is the simulation's world, turned
    /// by `simulationRotation` — see [ClothSimulationInstance#prepareFrame].
    public void drawClothMeshImmediate(
        final ClothSimulationInstance instance,
        final AssetAccessor<? extends Armature> armature,
        final Pose animationPose,
        final @Nullable ArmaturePhysicsData armaturePhysics,
        final ClothSimulationParameters parameters,
        final RenderType renderType,
        final PoseStack.Pose pose,
        final Matrix4f simulationRotation,
        final int frame,
        final int packedLight,
        final int packedOverlay,
        final int color
    ) {
        AnimationComputeShader meshShader = instance.getSetup().getMeshShader();

        prepareCloth(instance, armature, animationPose, armaturePhysics, parameters, pose, Vec3.ZERO, simulationRotation, frame, packedLight, packedOverlay, color, 0).run();
        draw(renderType, meshShader.output(), meshShader.vertexCount());
    }

    public void renderSolid() {
        render(solidSubmits);
    }

    public void renderTranslucent() {
        render(translucentSubmits);
    }

    public void clear() {
        solidSubmits.values().forEach(List::clear);
        translucentSubmits.values().forEach(List::clear);
    }

    public void endFrame() {
        if (frameStorage != null) {
            frameStorage.endFrame();
        }

        frameIndex++;

        clothInstances.values().removeIf(instance -> {
            boolean stale = instance.getLastFrame() < frameIndex - STALE_FRAMES;

            if (stale) instance.close();

            return stale;
        });
    }

    /// Closes every in-game cloth instance. The meshes they borrow from are about to close.
    public void releaseClothInstances() {
        clothInstances.values().forEach(ClothSimulationInstance::close);
        clothInstances.clear();
    }

    private DynamicStorageBuffer frameStorage() {
        if (frameStorage == null) {
            frameStorage = new DynamicStorageBuffer("akythera:skinning_payloads");
        }

        return frameStorage;
    }

    /// Resolves the bound skeleton and writes this draw's pose and part visibility into
    /// [#frameStorage]. The slices stay valid until [#endFrame].
    private SkinnedDrawContext buildContext(
        final SkeletalMesh mesh,
        final AssetAccessor<? extends Armature> armature,
        final Pose animationPose,
        final PoseStack.Pose pose,
        final int packedLight,
        final int packedOverlay,
        final int color,
        final int outlineColor
    ) {
        Armature resolved = armature.get();
        int boneCount = resolved.getBoneCount();

        if (boundTransforms.length < boneCount) {
            boundTransforms = Matrix4fUtils.allocateMatrixArray(boneCount);
        }

        animationPose.getForwardBoundTransforms(armature, boundTransforms);
        resolved.boundBackwardTransform(boundTransforms);

        DynamicStorageBuffer storage = frameStorage();
        GpuBufferSlice poses = storage.write(
            boneCount * MATRIX_BYTES, buffer -> writePoses(boundTransforms, boneCount, buffer)
        );
        GpuBufferSlice visibility = storage.write(
            visibilityWordCount(mesh) * FLAG_WORD_BYTES, buffer -> writeVisibility(mesh, buffer)
        );

        return new SkinnedDrawContext(
            poses,
            visibility,
            new Matrix4f(pose.pose()),
            new Matrix3f(pose.normal()),
            color,
            outlineColor,
            packedLight,
            packedOverlay
        );
    }

    /// Writes a cloth's frame inputs, poses and draw params, and returns the dispatch that simulates
    /// and skins it. The colliders read the animated bone transforms before the backward bind.
    private Runnable prepareCloth(
        final ClothSimulationInstance instance,
        final AssetAccessor<? extends Armature> armature,
        final Pose animationPose,
        final @Nullable ArmaturePhysicsData armaturePhysics,
        final ClothSimulationParameters parameters,
        final PoseStack.Pose pose,
        final Vec3 cameraPosition,
        final Matrix4f simulationRotation,
        final int frame,
        final int packedLight,
        final int packedOverlay,
        final int color,
        final int outlineColor
    ) {
        Armature resolved = armature.get();
        int boneCount = resolved.getBoneCount();

        if (boundTransforms.length < boneCount) {
            boundTransforms = Matrix4fUtils.allocateMatrixArray(boneCount);
        }

        animationPose.getForwardBoundTransforms(armature, boundTransforms);

        DynamicStorageBuffer storage = frameStorage();
        ClothSimulationInstance.Frame clothFrame = instance.prepareFrame(
            storage, pose.pose(), cameraPosition, simulationRotation, boundTransforms, resolved, armaturePhysics, parameters, frame
        );

        resolved.boundBackwardTransform(boundTransforms);

        ClothComputeShader setup = instance.getSetup();
        SkeletalMesh mesh = setup.getMesh();
        Matrix4f modelView = new Matrix4f(pose.pose());
        Matrix4f normal = new Matrix4f(pose.normal());

        GpuBufferSlice poses = storage.write(
            boneCount * MATRIX_BYTES, buffer -> writePoses(boundTransforms, boneCount, buffer)
        );
        GpuBufferSlice drawParams = storage.write(
            CLOTH_DRAW_HEADER_BYTES + visibilityWordCount(mesh) * FLAG_WORD_BYTES,
            buffer -> {
                modelView.get(0, buffer);
                normal.get(64, buffer);
                clothFrame.simulationToCamera().get(128, buffer);
                // The color attribute is four normalized unsigned bytes read as RGBA
                buffer.putInt(192, ARGB.toABGR(color));
                buffer.putInt(196, ARGB.toABGR(outlineColor));
                buffer.putInt(200, packedOverlay);
                buffer.putInt(204, packedLight);
                buffer.position(CLOTH_DRAW_HEADER_BYTES);
                writeVisibility(mesh, buffer);
            }
        );

        return () -> {
            instance.step(clothFrame, poses);
            setup.skin(instance, poses, drawParams);
        };
    }

    private void render(final Map<RenderType, List<SkinnedSubmit>> submits) {
        for (Map.Entry<RenderType, List<SkinnedSubmit>> entry : submits.entrySet()) {
            for (SkinnedSubmit submit : entry.getValue()) {
                AnimationComputeShader setup = submit.setup();

                submit.dispatch().run();

                drawSkinned(entry.getKey(), setup, CAMERA);

                Optional<RenderType> outline = entry.getKey().outline();
                GpuBuffer outlineVertices = setup.outlineOutput();

                if (submit.outlineColor() != 0 && outline.isPresent() && outlineVertices != null) {
                    draw(outline.get(), outlineVertices, setup.vertexCount());
                }
            }
        }
    }

    /// `PreparedRenderType#drawFromBuffer` fed the buffer the skinning dispatch already filled
    /// instead of a staged one, and the sequential index buffer, since the skinned vertices are a
    /// plain triangle list. `PrimitiveTopology.QUADS`, which the entity pipelines declare, is
    /// `GL_TRIANGLES` all the same (`GlConst#toGl`), so the pipeline needs no substitution.
    ///
    /// [RenderType#prepare] resolves the render type's textures and writes its dynamic transforms,
    /// both of which reach the command encoder on a miss. It runs before `drawFromBuffer` opens the
    /// render pass, which the encoder would reject either under.
    /// Draws `setup`'s skinned output. A render type that sorts on upload is translucent: its triangles are sorted
    /// farthest from `eye` first, and only the fragments nearest the camera are colored ([RenderUtils#drawBuffers]).
    private void drawSkinned(final RenderType renderType, final AnimationComputeShader setup, final Vector3fc eye) {
        if (!renderType.sortOnUpload()) {
            draw(renderType, setup.output(), setup.vertexCount());
            return;
        }

        TriangleDepthSort depthSort = setup.depthSort();
        GpuBuffer sortedIndices = depthSort.sort(setup.output(), eye);

        RenderUtils.drawBuffers(renderType, setup.output().slice(), sortedIndices, IndexType.INT, 0, depthSort.indexCount(), true);
    }

    private void draw(final RenderType renderType, final GpuBuffer vertices, final int vertexCount) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);

        renderType.prepare().drawFromBuffer(vertices, indices.getBuffer(vertexCount), indices.type(), 0, 0, vertexCount);
    }

    private static int visibilityWordCount(final SkeletalMesh mesh) {
        return (mesh.getParts().size() + 31) / 32;
    }

    private static void writePoses(final Matrix4f[] transforms, final int boneCount, final ByteBuffer buffer) {
        for (int i = 0; i < boneCount; i++) {
            transforms[i].get(i * MATRIX_BYTES, buffer);
        }
    }

    /// One bit per part in `SkeletalMesh#getParts` order, set when the part is hidden. The compute
    /// shader indexes it with the part index baked into the vertex buffer, which is the same order.
    private static void writeVisibility(final SkeletalMesh mesh, final ByteBuffer buffer) {
        int index = 0;
        int word = 0;

        for (Mesh.MeshPart part : mesh.getParts().values()) {
            if (!part.visible()) {
                word |= 1 << (index % 32);
            }

            index++;

            if (index % 32 == 0) {
                buffer.putInt(word);
                word = 0;
            }
        }

        if (index % 32 != 0) {
            buffer.putInt(word);
        }
    }

    /// One renderable, what it is attached to, and the transform that places it.
    ///
    /// @param space the renderable itself when it is a [TransformComponent], else its nearest one above; null when
    ///              there is none
    public record RenderableSubmit(
        RenderableComponent<?> component,
        ComponentAttachable host,
        @Nullable TransformComponent space
    ) {
    }

    /// How [LivingEntityRenderer#submit] would have drawn the entity's own model: what a patch's
    /// renderables draw with when they name nothing of their own.
    public record ModelDraw(RenderType renderType, int overlayCoords, int tintedColor) {
    }

    /// A queued draw: `dispatch` fills `setup`'s outputs, which are then drawn.
    private record SkinnedSubmit(AnimationComputeShader setup, Runnable dispatch, int outlineColor) {
    }
}
