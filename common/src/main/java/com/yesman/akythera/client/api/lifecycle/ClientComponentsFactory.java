package com.yesman.akythera.client.api.lifecycle;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
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

    // *********************************************************
    //  Equipment Part
    // *********************************************************
    private ArmorModelResolver armorModelResolver = (_, _, model) -> model;

    /// Sets how the model an item replaces its wearer's armor model with is read.
    public void setArmorModelResolver(final ArmorModelResolver armorModelResolver) {
        this.armorModelResolver = armorModelResolver;
    }

    /// The model `stack` is worn as, given `model`, the one its wearer's renderer wears that slot with.
    public Model<?> getArmorModel(final ItemStack stack, final EquipmentClientInfo.LayerType layerType, final Model<?> model) {
        return armorModelResolver.resolve(stack, layerType, model);
    }

    private ArmorTextureResolver armorTextureResolver = (_, _, _, texture) -> texture;

    /// Sets how the texture an item replaces one of its equipment layers' with is read.
    public void setArmorTextureResolver(final ArmorTextureResolver armorTextureResolver) {
        this.armorTextureResolver = armorTextureResolver;
    }

    /// The texture `layer` of `stack` is drawn with, given `texture`, the one its equipment asset names.
    public Identifier getArmorTexture(
        final ItemStack stack, final EquipmentClientInfo.LayerType layerType, final EquipmentClientInfo.Layer layer, final Identifier texture
    ) {
        return armorTextureResolver.resolve(stack, layerType, layer, texture);
    }

    private ArmorTintResolver armorTintResolver = (stack, layer, _) -> {
        int dyeColor = DyedItemColor.getOrDefault(stack, 0);

        return layer.dyeable().isEmpty()
            ? -1
            : dyeColor != 0 ? dyeColor : layer.dyeable().get().colorWhenUndyed().map(ARGB::opaque).orElse(0);
    };

    /// Sets how the color an item tints one of its equipment layers with is read.
    public void setArmorTintResolver(final ArmorTintResolver armorTintResolver) {
        this.armorTintResolver = armorTintResolver;
    }

    /// The ARGB color the `layerIndex`-th equipment layer of `stack` is tinted with, 0 for a layer that is not drawn.
    public int getArmorTint(final ItemStack stack, final EquipmentClientInfo.Layer layer, final int layerIndex) {
        return armorTintResolver.resolve(stack, layer, layerIndex);
    }

    private ArmorSubmitter armorSubmitter = (_, _, _, _, _, _, _) -> false;

    /// Sets how an item that draws its own armor is drawn.
    public void setArmorSubmitter(final ArmorSubmitter armorSubmitter) {
        this.armorSubmitter = armorSubmitter;
    }

    /// Draws `stack` through the renderer its item registered for itself, in the space of `parentModel`. False
    /// when the item registered none.
    public boolean submitArmor(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final ItemStack stack,
        final LivingEntityRenderState state,
        final EquipmentSlot slot,
        final int lightCoords,
        final Model<?> parentModel
    ) {
        return armorSubmitter.submit(poseStack, submitNodeCollector, stack, state, slot, lightCoords, parentModel);
    }

    /// Reads the model an item is worn as through the mod loader's own item hook.
    @FunctionalInterface
    public interface ArmorModelResolver {
        /// The model `stack` is worn as, `model` itself for an item that replaces nothing.
        Model<?> resolve(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model<?> model);
    }

    /// Reads the texture of an equipment layer through the mod loader's own item hook.
    @FunctionalInterface
    public interface ArmorTextureResolver {
        /// The texture `layer` is drawn with, `texture` itself for an item that replaces nothing.
        Identifier resolve(ItemStack stack, EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer layer, Identifier texture);
    }

    /// Reads the tint of an equipment layer through the mod loader's own item hook.
    @FunctionalInterface
    public interface ArmorTintResolver {
        /// The ARGB color the `layerIndex`-th layer is tinted with, 0 for a layer that is not drawn.
        int resolve(ItemStack stack, EquipmentClientInfo.Layer layer, int layerIndex);
    }

    /// Draws an item through the armor renderer the mod loader keeps for it.
    @FunctionalInterface
    public interface ArmorSubmitter {
        /// Whether `stack` was drawn.
        boolean submit(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            ItemStack stack,
            LivingEntityRenderState state,
            EquipmentSlot slot,
            int lightCoords,
            Model<?> parentModel
        );
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
