package com.yesman.akythera.mixinaccessor.client;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.collider.ColliderDebugRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface LivingEntityRenderStateAccessor {
    /// The renderables collected off the patch this frame. Mutable and live: the extract pass clears
    /// and refills it.
    List<PatchModelRenderer.RenderableSubmit> akythera$getRenderables();

    /// The colliders collected off the patch this frame, empty while the hitbox scene is closed.
    /// Mutable and live: the extract pass clears and refills it.
    List<ColliderDebugRenderer.ColliderSubmit> akythera$getColliders();

    void akythera$setModelDraw(PatchModelRenderer.@Nullable ModelDraw modelDraw);

    PatchModelRenderer.@Nullable ModelDraw akythera$getModelDraw();

    /// Whether the entity's own vanilla model and its layers are skipped this frame, see
    /// [EntityPatch#isBlockingVanillaRender].
    boolean akythera$isVanillaModelHidden();

    void akythera$setVanillaModelHidden(boolean vanillaModelHidden);

    /// Whether the entity's model is placed by its armature alone: the renderer skips its own posing (the sleeping,
    /// dying, swimming and elytra rotations, its own scale, the bed and crouch offsets), so the model sits where
    /// [EntityPatch#getModelTransform] puts it.
    boolean akythera$isArmaturePlaced();

    void akythera$setArmaturePlaced(boolean armaturePlaced);

    /// The item render state for one slot in one display context, created on first request and reused
    /// every frame after.
    ItemStackRenderState akythera$itemState(EquipmentSlot slot, ItemDisplayContext displayContext);
}
