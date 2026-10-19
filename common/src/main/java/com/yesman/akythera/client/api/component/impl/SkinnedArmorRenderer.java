package com.yesman.akythera.client.api.component.impl;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.VanillaModelBaker;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Draws the armor piece worn in one armor slot ([EquipmentSlot#isArmor]), skinned.
///
/// The piece is the model the entity's own renderer wears in that slot, rebuilt on [#armature] by
/// [VanillaModelBaker] and skinned by its pose, in the armature's space. An item that draws its own armor
/// is drawn by its own renderer instead, rigid in that space.
///
/// Instantiated per host by [SkinnedArmorRendererBlueprint].
public final class SkinnedArmorRenderer implements RenderableComponent<LivingEntityRenderState>, TransformComponent {
    /// The slot whose armor piece is drawn.
    private final EquipmentSlot equipmentSlot;

    /// The armature posing the piece, null where the host chain reaches none.
    private final @Nullable ArmatureComponent armature;

    /// The host this component is attached under.
    private final @Nullable ComponentAttachable host;

    /// Where the piece sits in [#armature]'s space.
    private Transform localTransform;

    /// The elements of that space the piece takes on.
    private final TransformInheritPolicy transformInheritPolicy;

    /// Whether this component takes part, see [PatchComponent#isEnabled].
    private boolean enabled = true;

    /// The worn piece's model rebuilt on [#armature], read on the extract pass. Null while nothing is baked.
    private @Nullable SkeletalMesh wornMesh;

    /// The stack worn in [#equipmentSlot], read on the extract pass.
    private ItemStack wornStack = ItemStack.EMPTY;

    /// The equipment layers [#wornStack] is read through.
    private EquipmentClientInfo.LayerType wornLayerType = EquipmentClientInfo.LayerType.HUMANOID;

    /// The model of the entity's renderer, read on the extract pass. Null while nothing is worn.
    private @Nullable Model<?> parentModel;

    public SkinnedArmorRenderer(
        final EquipmentSlot equipmentSlot,
        final @Nullable ArmatureComponent armature,
        final @Nullable ComponentAttachable host,
        final Transform localTransform,
        final TransformInheritPolicy transformInheritPolicy
    ) {
        this.equipmentSlot = equipmentSlot;
        this.armature = armature;
        this.host = host;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// The model `renderer` wears `stack` in `slot` with and the equipment layers it reads, or null when
    /// it wears nothing there.
    public static @Nullable WornModel wornModel(
        final @Nullable EntityRenderer<?, ?> renderer, final ItemStack stack, final EquipmentSlot slot, final boolean baby
    ) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return null;

        for (RenderLayer<?, ?> layer : livingRenderer.layers) {
            EquipmentClientInfo.LayerType layerType;
            Model<?> model;

            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && layer instanceof HumanoidArmorLayer<?, ?, ?> armorLayer) {
                layerType = baby
                    ? EquipmentClientInfo.LayerType.HUMANOID_BABY
                    : slot == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
                model = (baby ? armorLayer.babyModelSet : armorLayer.modelSet).get(slot);
            } else if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR
                && layer instanceof SimpleEquipmentLayer<?, ?, ?> equipmentLayer
                && !wornLayers(stack, slot, equipmentLayer.layer).isEmpty()
            ) {
                layerType = equipmentLayer.layer;
                model = baby ? equipmentLayer.babyModel : equipmentLayer.adultModel;
            } else {
                continue;
            }

            return model == null
                ? null
                : new WornModel(ClientComponentsFactory.getImpl().getArmorModel(stack, layerType, model), layerType);
        }

        return null;
    }

    /// The layers `stack` is drawn with while worn in `slot`, empty for a stack that is not worn there.
    public static List<EquipmentClientInfo.Layer> wornLayers(
        final ItemStack stack, final EquipmentSlot slot, final EquipmentClientInfo.LayerType layerType
    ) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);

        if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) return List.of();

        return Minecraft.getInstance().getEntityRenderDispatcher().equipmentAssets
            .get(equippable.assetId().get())
            .getLayers(layerType);
    }

    /// A model a renderer wears one slot with.
    ///
    /// @param layerType the equipment layers the worn stack is read through
    public record WornModel(Model<?> model, EquipmentClientInfo.LayerType layerType) {
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    // ***************************************************************
    // RenderableComponent
    // ***************************************************************

    @Override
    public Class<LivingEntityRenderState> stateType() {
        return LivingEntityRenderState.class;
    }

    /// Resolves the worn piece into [#wornMesh].
    @Override
    public void extract(final EntityPatch<?> patch, final LivingEntityRenderState state, final float partialTick) {
        if (!(patch.getOriginal() instanceof LivingEntity livingEntity)) return;

        wornMesh = null;
        parentModel = null;
        wornStack = livingEntity.getItemBySlot(equipmentSlot);

        if (armature == null || wornStack.isEmpty()) return;

        Armature resolvedArmature = armature.getArmature().getOrHandleException(Akythera.LOGGER::error);
        EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(livingEntity);

        if (resolvedArmature == null || !(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return;

        parentModel = livingRenderer.getModel();

        WornModel wornModel = wornModel(
            renderer, wornStack, equipmentSlot, livingEntity.isBaby() && livingEntity.getType() != EntityTypes.ARMOR_STAND
        );

        if (wornModel == null || !HumanoidArmorLayer.shouldRender(wornStack, equipmentSlot)) return;

        wornLayerType = wornModel.layerType();

        List<Identifier> textures = wornLayers(wornStack, equipmentSlot, wornLayerType).stream()
            .map(layer -> ClientComponentsFactory.getImpl().getArmorTexture(
                wornStack, wornLayerType, layer, layer.getTextureLocation(wornLayerType)
            ))
            .toList();

        for (PatchComponent sibling : armature.getComponents().values()) {
            if (sibling instanceof MeshComponent body
                && body.isEnabled()
                && body.getMesh() != null
                && body.getMesh().getOrHandleException(Akythera.LOGGER::error) instanceof SkeletalMesh bodyMesh
            ) {
                wornMesh = VanillaModelBaker.bake(
                    wornModel.model(), resolvedArmature, bodyMesh, wornStack.getItem(), equipmentSlot, textures
                );
                break;
            }
        }
    }

    @Override
    public void submit(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state,
        final float yRot,
        final float xRot
    ) {
        if (parentModel == null) return;

        // An armor renderer of the mod loader draws in the vanilla model's space and off its parts
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, EntityModel.MODEL_Y_OFFSET, 0.0F);
        parentModel.resetPose();

        boolean selfDrawn = ClientComponentsFactory.getImpl().submitArmor(
            poseStack, submitNodeCollector, wornStack, state, equipmentSlot, lightCoords, parentModel
        );

        poseStack.popPose();

        if (selfDrawn || wornMesh == null) return;

        List<EquipmentClientInfo.Layer> layers = wornLayers(wornStack, equipmentSlot, wornLayerType);

        if (layers.isEmpty()) return;

        boolean foil = wornStack.hasFoil();

        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            EquipmentClientInfo.Layer layer = layers.get(layerIndex);
            int color = ClientComponentsFactory.getImpl().getArmorTint(wornStack, layer, layerIndex);

            if (color == 0) continue;

            submitWorn(
                poseStack, submitNodeCollector, lightCoords, state,
                RenderTypes.armorCutoutNoCull(ClientComponentsFactory.getImpl().getArmorTexture(
                    wornStack, wornLayerType, layer, layer.getTextureLocation(wornLayerType)
                )),
                color, null
            );

            if (foil) {
                submitWorn(poseStack, submitNodeCollector, lightCoords, state, RenderTypes.armorEntityGlint(), color, null);
            }

            foil = false;
        }

        ArmorTrim trim = wornStack.get(DataComponents.TRIM);
        Equippable equippable = wornStack.get(DataComponents.EQUIPPABLE);

        if (trim != null && equippable != null && wornLayerType != EquipmentClientInfo.LayerType.HUMANOID_BABY) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.ARMOR_TRIMS)
                .getSprite(trim.layerAssetId(wornLayerType.trimAssetPrefix(), equippable.assetId().orElseThrow()));

            submitWorn(
                poseStack, submitNodeCollector, lightCoords, state,
                Sheets.armorTrimsSheet(trim.pattern().value().decal()), -1, sprite
            );
        }
    }

    /// Draws [#wornMesh] once through `renderType`, its UVs laid into `sprite` when one is given.
    ///
    /// The skinning dispatch writes entity vertices with the mesh's own UVs. A pass that takes another
    /// vertex format, or that draws through a sprite, is skinned into the staged buffer instead.
    private void submitWorn(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state,
        final RenderType renderType,
        final int color,
        final @Nullable TextureAtlasSprite sprite
    ) {
        SkeletalMesh mesh = wornMesh;
        Pose animationPose = armature == null ? null : armature.getCachedPose();

        if (mesh == null || animationPose == null) return;

        AssetAccessor<? extends Armature> armatureAsset = armature.getArmature();

        if (sprite == null && mesh.getAnimationComputeShader() != null && renderType.format() == DefaultVertexFormat.ENTITY) {
            PatchModelRenderer.getInstance().submitSkinnedMesh(
                mesh, armatureAsset, animationPose, renderType, poseStack.last(),
                lightCoords, OverlayTexture.NO_OVERLAY, color, state.outlineColor
            );

            return;
        }

        Armature resolvedArmature = armatureAsset.get();
        Matrix4f[] boundTransforms = animationPose.getForwardBoundTransforms(
            armatureAsset, Matrix4fUtils.allocateMatrixArray(resolvedArmature.getBoneCount())
        );

        resolvedArmature.boundBackwardTransform(boundTransforms);

        submitNodeCollector.submitCustomGeometry(
            poseStack,
            renderType,
            (pose, buffer) -> {
                // SkeletalMesh#drawPosed reads a pose stack; this one carries the submitted pose alone
                PoseStack skinningStack = new PoseStack();
                skinningStack.last().pose().set(pose.pose());
                skinningStack.last().normal().set(pose.normal());

                mesh.drawPosed(
                    skinningStack,
                    sprite == null ? buffer : sprite.wrap(buffer),
                    Mesh.DrawingFunction.trianglesAsQuads(Mesh.DrawingFunction.NEW_ENTITY),
                    lightCoords,
                    ARGB.redFloat(color),
                    ARGB.greenFloat(color),
                    ARGB.blueFloat(color),
                    ARGB.alphaFloat(color),
                    OverlayTexture.NO_OVERLAY,
                    boundTransforms
                );
            }
        );
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
    public @Nullable ComponentAttachable host() {
        return host;
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return transformInheritPolicy;
    }

    /// The piece sits in its armature's space.
    @Override
    public Transform parentModelTransform() {
        return armature != null ? armature.modelTransform() : TransformComponent.super.parentModelTransform();
    }

    @Override
    public Transform previousParentModelTransform() {
        return armature != null ? armature.previousModelTransform() : TransformComponent.super.previousParentModelTransform();
    }
}
