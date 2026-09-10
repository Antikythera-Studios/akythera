package com.yesman.akythera.mixinaccessor.client;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.component.impl.EntityModelComponent;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.collider.ColliderDebugRenderer;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface LivingEntityRenderStateAccessor {
    void akythera$setPose(Pose pose);

    Pose akythera$getPose();

    void akythera$setArmature(AssetAccessor<? extends Armature> pose);

    AssetAccessor<? extends Armature> akythera$getArmature();

    /// The renderables collected off the patch this frame. Mutable and live: the extract pass clears
    /// and refills it.
    List<PatchModelRenderer.RenderableSubmit> akythera$getRenderables();

    /// The colliders collected off the patch this frame, empty while the hitbox scene is closed.
    /// Mutable and live: the extract pass clears and refills it.
    List<ColliderDebugRenderer.ColliderSubmit> akythera$getColliders();

    void akythera$setModelSubmit(EntityModelComponent.@Nullable ModelSubmit modelSubmit);

    EntityModelComponent.@Nullable ModelSubmit akythera$getModelSubmit();

    /// The item render state for one slot in one display context, created on first request and reused
    /// every frame after.
    ItemStackRenderState akythera$itemState(EquipmentSlot slot, ItemDisplayContext displayContext);
}
