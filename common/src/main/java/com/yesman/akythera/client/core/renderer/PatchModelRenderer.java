package com.yesman.akythera.client.core.renderer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.shader.backend.setup.AnimationComputeShader;
import com.yesman.akythera.client.core.shader.backend.setup.DynamicStorageBuffer;
import com.yesman.akythera.client.core.shader.backend.setup.SkinnedDrawContext;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
/// `FeatureRenderDispatcher.PreparedFrame` drives [#renderSolid], [#renderTranslucent], [#clear]
/// and [#endFrame].
///
/// [#drawSkinnedMeshImmediate] is the way in for callers that reach neither phase.
public final class PatchModelRenderer {
    // 16(elements) x 4(bytes)
    private static final int MATRIX_BYTES = 64;
    // Up to 32 part visibility flags are available
    private static final int FLAG_WORD_BYTES = 4;

    private static final PatchModelRenderer INSTANCE = new PatchModelRenderer();

    public static PatchModelRenderer getInstance() {
        return INSTANCE;
    }

    /// Every renderable under `host`, parents before children, each paired with the [BoneSocket] it
    /// hangs under. `socket` is the one `host` itself sits under — null at the patch root.
    public static void collect(
        final ComponentAttachable host,
        final @Nullable BoneSocket socket,
        final List<RenderableSubmit> out
    ) {
        for (PatchComponent component : host.getComponents().values()) {
            if (component instanceof RenderableComponent<?> renderable) {
                out.add(new RenderableSubmit(renderable, socket));
            }

            if (component instanceof ComponentAttachable attachable) {
                collect(attachable, component instanceof BoneSocket boneSocket ? boneSocket : socket, out);
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
                ((RenderableComponent<LivingEntityRenderState>) component).extract(patch, state, partialTick);
            }
        }
    }

    /// Submits every collected renderable the state fits, each under its socket's transform.
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

            if (submit.socket() != null) {
                RenderUtils.toArmatureSpace(poseStack);
                poseStack.mulPose(submit.socket().getBoundTransform().toMatrix());
            }

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

        SkinnedSubmit submit = new SkinnedSubmit(
            setup,
            buildContext(mesh, armature, animationPose, pose, packedLight, packedOverlay, color, outlineColor),
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
        final int color
    ) {
        AnimationComputeShader setup = mesh.getAnimationComputeShader();
        if (setup == null) return;

        setup.skin(buildContext(mesh, armature, animationPose, pose, packedLight, packedOverlay, color, 0));
        draw(renderType, setup.output(), setup.vertexCount());
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

    private void render(final Map<RenderType, List<SkinnedSubmit>> submits) {
        for (Map.Entry<RenderType, List<SkinnedSubmit>> entry : submits.entrySet()) {
            for (SkinnedSubmit submit : entry.getValue()) {
                AnimationComputeShader setup = submit.setup();

                setup.skin(submit.context());
                draw(entry.getKey(), setup.output(), setup.vertexCount());

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

    /// One renderable and the socket it draws under, or null when it hangs off the patch directly.
    public record RenderableSubmit(RenderableComponent<?> component, @Nullable BoneSocket socket) {
    }

    private record SkinnedSubmit(AnimationComputeShader setup, SkinnedDrawContext context, int outlineColor) {
    }
}
