package com.yesman.akythera.client.api.lifecycle;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.physics.cloth.ClothColliderPacker;
import com.yesman.akythera.client.core.renderer.TrailRenderer;
import com.yesman.akythera.client.core.shader.backend.ComputeBackends;
import com.yesman.akythera.client.core.shader.backend.setup.AnimationComputeShader;
import com.yesman.akythera.client.core.shader.backend.setup.ClothComputeShader;
import com.yesman.akythera.client.core.shader.backend.setup.ClothProgram;
import com.yesman.akythera.client.core.shader.backend.setup.TriangleDepthSort;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/// *Physical client*(commonly refers Minecraft launcher) side components factory.
@ClientOnly
public final class ClientComponentsFactory extends ComponentsFactory {
    /// Returns client component factory by force casting
    public static ClientComponentsFactory getImpl() {
        return (ClientComponentsFactory)ComponentsFactory.getImpl();
    }

    @Override
    public ResourceManager getResourceManager() {
        return Minecraft.getInstance().getResourceManager();
    }

    @Override
    public @Nullable PlayerSkin getPlayerSkin(final Player player) {
        if (player instanceof AbstractClientPlayer abstractClientPlayer) return abstractClientPlayer.getSkin();

        return null;
    }

    // *********************************************************
    //  Compute Shader Part
    // *********************************************************
    private GraphicsApi graphicsApi = GraphicsApi.UNKNOWN;
    private ComputeShaderSupport supportComputeShader = ComputeShaderSupport.UNSUPPORTED;
    private ShaderBackend shaderBackend;
    private Function<SkeletalMesh.SkeletalMeshBuilder, AnimationComputeShader> factory;
    private Runnable programInvalidator = () -> {};

    public void inspectDeviceSpec() {
        DeviceInfo deviceInfo = RenderSystem.getDevice().getDeviceInfo();

        graphicsApi = GraphicsApi.of(deviceInfo.backendName());
        supportComputeShader = switch (graphicsApi) {
            case OPENGL -> inspectOpenGlSupport();
            // Compute shaders, storage buffers and the packing built-ins are all core Vulkan 1.0,
            // so a device that runs the Vulkan backend at all runs the compute path.
            case VULKAN -> ComputeShaderSupport.CORE;
            case UNKNOWN -> ComputeShaderSupport.UNSUPPORTED;
        };

        Akythera.LOGGER.info(
            "[Akythera] Graphics backend: {}, compute shader support type: {}", deviceInfo.backendName(), supportComputeShader.name()
        );

        if (!supportComputeShader()) {
            Akythera.LOGGER.warn("Compute shader is unsupported in this device. Some features (frame acceleration, cloth simulation) are not supported");
        }
    }

    /// Minecraft asks for a 3.3 core profile, so GL_MAJOR_VERSION reads 3.3 on every device and says
    /// nothing about what the driver can do. The extension strings are what the driver still
    /// advertises, and every entry point and GLSL construct the compute path uses has to be present
    /// among them.
    private static ComputeShaderSupport inspectOpenGlSupport() {
        GLCapabilities caps = GL.getCapabilities();

        if (caps.OpenGL43) {
            return ComputeShaderSupport.CORE;
        }

        boolean arb = caps.GL_ARB_compute_shader                  // glDispatchCompute, layout(local_size_x)
            && caps.GL_ARB_shader_storage_buffer_object           // layout(std430) buffer, .length()
            && caps.GL_ARB_shader_image_load_store                // glMemoryBarrier, readonly/writeonly
            && caps.GL_ARB_shading_language_packing               // packUnorm4x8, packSnorm4x8
            && caps.GL_ARB_explicit_uniform_location              // layout(location = N) uniform
            && caps.GL_ARB_separate_shader_objects;               // glProgramUniform*

        return arb ? ComputeShaderSupport.ABR : ComputeShaderSupport.UNSUPPORTED;
    }

    /// The graphics API the compute path has to speak this run.
    public GraphicsApi getGraphicsApi() {
        return graphicsApi;
    }

    /// Initialize compute shader provider
    public void initComputeShaderFactory(
        final ShaderBackend shaderBackend,
        final Function<SkeletalMesh.SkeletalMeshBuilder, AnimationComputeShader> factory,
        final Runnable programInvalidator
    ) {
        this.shaderBackend = shaderBackend;
        this.factory = factory;
        this.programInvalidator = programInvalidator;
    }

    /// Drops the compute programs the backend holds, so the next dispatch compiles them from the
    /// reloaded resource pack.
    public void invalidateComputeShaderPrograms() {
        programInvalidator.run();
        ClothProgram.invalidateAll();
        TriangleDepthSort.invalidateProgram();
        TrailRenderer.invalidateProgram();
    }

    public boolean supportComputeShader() {
        return supportComputeShader != ComputeShaderSupport.UNSUPPORTED;
    }

    public boolean isABRSupport() {
        return supportComputeShader == ComputeShaderSupport.ABR;
    }

    /// Patch [SkeletalMesh] to make it available by compute shaders.
    public AnimationComputeShader getAnimationComputeShaderSetup(final SkeletalMesh.SkeletalMeshBuilder skeletalMesh) {
        if (!supportComputeShader()) {
            return null;
        }

        return factory.apply(skeletalMesh);
    }

    // *********************************************************
    //  Cloth Simulation Part
    // *********************************************************
    private final Map<ColliderType, ClothColliderPacker<?>> clothColliderPackers = new HashMap<>();

    /// Whether this run simulates cloth: compute support, the vanilla shader backend, and at least
    /// [ClothComputeShader#REQUIRED_STORAGE_BLOCKS] storage buffers per compute stage.
    public boolean supportClothSimulation() {
        return supportComputeShader()
            && shaderBackend == ShaderBackend.VANILLA
            && ComputeBackends.get().maxComputeStorageBlocks() >= ClothComputeShader.REQUIRED_STORAGE_BLOCKS;
    }

    /// The cloth residency of `mesh`, or null when cloth is unsupported or `mesh` has no skinning
    /// residency to borrow.
    public @Nullable ClothComputeShader createClothComputeShader(final SkeletalMesh mesh, final float[] weights, final int[] offsetPairs) {
        if (!supportClothSimulation() || mesh.getAnimationComputeShader() == null) {
            return null;
        }

        return new ClothComputeShader(mesh, weights, offsetPairs);
    }

    /// Registers how a collider type reaches the cloth solver. Registering a type twice replaces it.
    public <T extends Collider> void registerClothColliderPacker(final ColliderType type, final ClothColliderPacker<T> packer) {
        clothColliderPackers.put(type, packer);
    }

    /// The packer of `collider`'s type, or null when cloth ignores that type.
    @SuppressWarnings("unchecked")
    public @Nullable ClothColliderPacker<Collider> getClothColliderPacker(final Collider collider) {
        return (ClothColliderPacker<Collider>) clothColliderPackers.get(collider.type());
    }

    public ShaderBackend getShaderBackend() {
        return shaderBackend;
    }

    public enum ShaderBackend {
        VANILLA(false), IRIS(true);

        final boolean modded;

        ShaderBackend(boolean modded) {
            this.modded = modded;
        }
    }

    public enum ComputeShaderSupport {
        CORE, ABR, UNSUPPORTED
    }

    /// The backends Minecraft picks between in `PreferredGraphicsApi#getBackendsToTry`, matched by
    /// the name each one reports through [DeviceInfo#backendName]. `GpuWarnlistManager` reads the
    /// same field to decide whether its OpenGL-only checks apply.
    public enum GraphicsApi {
        OPENGL("OpenGL"), VULKAN("Vulkan"), UNKNOWN("");

        private final String backendName;

        GraphicsApi(String backendName) {
            this.backendName = backendName;
        }

        static GraphicsApi of(final String backendName) {
            for (GraphicsApi api : values()) {
                if (api.backendName.equals(backendName)) return api;
            }

            return UNKNOWN;
        }
    }
}
