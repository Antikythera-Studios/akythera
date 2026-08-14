package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.component.impl.EntityModelComponent;
import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mixin(LivingEntityRenderState.class)
public abstract class MixinLivingEntityRenderState implements LivingEntityRenderStateAccessor {
    @Unique @Nullable
    private Pose epicfight$pose;

    @Unique @Nullable
    private AssetAccessor<? extends Armature> epicfight$armature;

    @Unique
    private final List<PatchModelRenderer.RenderableSubmit> epicfight$renderables = new ArrayList<>();

    @Unique
    private EntityModelComponent.@Nullable ModelSubmit epicfight$modelSubmit;

    @Unique
    private final Map<EquipmentSlot, Map<ItemDisplayContext, ItemStackRenderState>> epicfight$itemStates =
        new EnumMap<>(EquipmentSlot.class);

    @Unique @Override
    public void akythera$setPose(Pose pose) {
        epicfight$pose = pose;
    }

    @Unique @Override
    public Pose akythera$getPose() {
        return epicfight$pose;
    }

    @Unique @Override
    public void akythera$setArmature(AssetAccessor<? extends Armature> pose) {
        epicfight$armature = pose;
    }

    @Unique @Override
    public AssetAccessor<? extends Armature> akythera$getArmature() {
        return epicfight$armature;
    }

    @Unique @Override
    public List<PatchModelRenderer.RenderableSubmit> akythera$getRenderables() {
        return epicfight$renderables;
    }

    @Unique @Override
    public void akythera$setModelSubmit(EntityModelComponent.@Nullable ModelSubmit modelSubmit) {
        epicfight$modelSubmit = modelSubmit;
    }

    @Unique @Override
    public EntityModelComponent.@Nullable ModelSubmit akythera$getModelSubmit() {
        return epicfight$modelSubmit;
    }

    @Unique @Override
    public ItemStackRenderState akythera$itemState(EquipmentSlot slot, ItemDisplayContext displayContext) {
        return epicfight$itemStates
            .computeIfAbsent(slot, _ -> new EnumMap<>(ItemDisplayContext.class))
            .computeIfAbsent(displayContext, _ -> new ItemStackRenderState());
    }
}
