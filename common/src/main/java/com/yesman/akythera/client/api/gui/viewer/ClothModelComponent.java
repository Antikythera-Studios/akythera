package com.yesman.akythera.client.api.gui.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.physics.ClothSimulationParameters;
import com.yesman.akythera.client.core.physics.cloth.ClothTopology;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.shader.backend.setup.ClothComputeShader;
import com.yesman.akythera.client.core.shader.backend.setup.ClothSimulationInstance;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/// A cloth simulated live in [Model3DViewer], over a skeletal mesh posed by [#poseSource].
///
/// The viewer orbits its camera around a model that never moves, so the model is turned instead:
/// [#simulationRotation] yaws the frame the cloth is simulated in with the camera, and the draw comes
/// back out through its inverse. The cloth lags behind that turn, which is the centrifugal force the
/// legacy `ModelPreviewer` produced the same way.
///
/// Draws nothing where [ClientComponentsFactory#supportClothSimulation] is false.
public final class ClothModelComponent implements Model3DViewerComponent {
    /// Vertex tint of the previewed cloth, ARGB.
    private static final int NO_TINT = 0xFFFFFFFF;

    /// Yaw the previewed model sits at when the camera is at its own. Inert — only changes in the
    /// simulation frame reach the cloth — and kept because it is the legacy previewer's number.
    private static final float FACING_CAMERA_YAW = 180.0F;

    private final AssetAccessor<? extends SkeletalMesh> mesh;
    private final AssetAccessor<? extends Armature> armature;

    /// Read per frame; a different array rebuilds the simulation.
    private final Supplier<float[]> weights;

    /// Read per frame; a different array rebuilds the simulation.
    private final Supplier<int[]> offsetPairs;

    private final Supplier<ClothSimulationParameters> parameters;
    private Supplier<@Nullable ArmaturePhysicsData> armaturePhysics = () -> null;
    private Supplier<@Nullable Identifier> texture = () -> null;

    /// The pose the mesh is skinned by, advanced by whoever owns it. Null holds the T-pose.
    private Supplier<@Nullable Pose> poseSource = () -> null;

    private @Nullable ClothComputeShader setup;
    private @Nullable ClothSimulationInstance instance;

    /// The weights [#setup] was built from.
    private float @Nullable [] builtWeights;

    /// The offset pairs [#setup] was built from.
    private int @Nullable [] builtOffsetPairs;

    private int frame;

    public ClothModelComponent(
        final AssetAccessor<? extends SkeletalMesh> mesh,
        final AssetAccessor<? extends Armature> armature,
        final Supplier<float[]> weights,
        final Supplier<int[]> offsetPairs,
        final Supplier<ClothSimulationParameters> parameters
    ) {
        this.mesh = mesh;
        this.armature = armature;
        this.weights = weights;
        this.offsetPairs = offsetPairs;
        this.parameters = parameters;
    }

    public void setArmaturePhysics(final Supplier<@Nullable ArmaturePhysicsData> armaturePhysics) {
        this.armaturePhysics = armaturePhysics;
    }

    /// Null from the supplier falls back to the mesh's sidecar texture.
    public void setTexture(final Supplier<@Nullable Identifier> texture) {
        this.texture = texture;
    }

    /// The pose to skin by, read per frame. This component never advances an animator of its own — the
    /// supplier's owner does, so one animator can pose the body and its cloth alike.
    public void setPoseSource(final Supplier<@Nullable Pose> poseSource) {
        this.poseSource = poseSource;
    }

    /// Re-skins the cloth onto the animated pose on the next frame.
    public void resetSimulation() {
        if (instance != null) instance.requestReset();
    }

    /// The topology currently simulated, or null before the first frame.
    public @Nullable ClothTopology getTopology() {
        return setup == null ? null : setup.getTopology();
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        if (!ClientComponentsFactory.getImpl().supportClothSimulation()) return;

        SkeletalMesh skeletalMesh = mesh.getOrHandleException(Akythera.LOGGER::error);
        Armature resolvedArmature = armature.getOrHandleException(Akythera.LOGGER::error);

        if (skeletalMesh == null || resolvedArmature == null || skeletalMesh.getAnimationComputeShader() == null) return;

        float[] currentWeights = weights.get();
        int[] currentOffsetPairs = offsetPairs.get();

        if (setup == null || instance == null || builtWeights != currentWeights || builtOffsetPairs != currentOffsetPairs || setup.getMesh() != skeletalMesh) {
            release();

            setup = new ClothComputeShader(skeletalMesh, currentWeights, currentOffsetPairs);
            instance = setup.createInstance();
            builtWeights = currentWeights;
            builtOffsetPairs = currentOffsetPairs;
        }

        Pose suppliedPose = poseSource.get();
        Pose pose = suppliedPose == null ? resolvedArmature.getTPose() : suppliedPose;

        Identifier supplied = texture.get();
        Identifier sidecar = skeletalMesh.getMetadata().texture();
        Identifier drawTexture = supplied != null ? supplied : sidecar != null ? sidecar : MissingTextureAtlasSprite.getLocation();

        PatchModelRenderer.getInstance().drawClothMeshImmediate(
            instance,
            armature,
            pose,
            armaturePhysics.get(),
            parameters.get(),
            RenderTypes.entityCutout(drawTexture),
            poseStack.last(),
            simulationRotation(model3DViewer),
            frame++,
            Model3DViewer.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            NO_TINT
        );
    }

    @Override
    public int drawOrder() {
        return 10;
    }

    /// The yaw the previewed model stands at in the world the cloth is simulated in: the camera's, so
    /// that orbiting the view turns the model under it. The draw undoes it, so nothing moves on screen
    /// — only the frame the particles are chasing does.
    private Matrix4f simulationRotation(final Model3DViewer model3DViewer) {
        CameraComponent camera = model3DViewer.getComponent(CameraComponent.class);

        if (camera == null) return new Matrix4f();

        return new Matrix4f().rotationY((FACING_CAMERA_YAW - camera.getYRot()) * Mth.DEG_TO_RAD);
    }

    @Override
    public void destroy() {
        release();
    }

    private void release() {
        if (instance != null) instance.close();
        if (setup != null) setup.close();

        instance = null;
        setup = null;
        builtWeights = null;
        builtOffsetPairs = null;
    }
}
