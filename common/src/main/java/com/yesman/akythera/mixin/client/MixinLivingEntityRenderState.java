package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.core.renderer.PatchModelRenderer;
import com.yesman.akythera.client.core.renderer.collider.ColliderDebugRenderer;
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
    @Unique
    private final List<PatchModelRenderer.RenderableSubmit> epicfight$renderables = new ArrayList<>();

    @Unique
    private final List<ColliderDebugRenderer.ColliderSubmit> akythera$colliders = new ArrayList<>();

    @Unique
    private PatchModelRenderer.@Nullable ModelDraw epicfight$modelDraw;

    @Unique
    private boolean akythera$vanillaModelHidden;

    @Unique
    private boolean akythera$armaturePlaced;

    @Unique
    private final Map<EquipmentSlot, Map<ItemDisplayContext, ItemStackRenderState>> epicfight$itemStates =
        new EnumMap<>(EquipmentSlot.class);

    @Unique @Override
    public List<PatchModelRenderer.RenderableSubmit> akythera$getRenderables() {
        return epicfight$renderables;
    }

    @Unique @Override
    public List<ColliderDebugRenderer.ColliderSubmit> akythera$getColliders() {
        return akythera$colliders;
    }

    @Unique @Override
    public void akythera$setModelDraw(PatchModelRenderer.@Nullable ModelDraw modelDraw) {
        epicfight$modelDraw = modelDraw;
    }

    @Unique @Override
    public PatchModelRenderer.@Nullable ModelDraw akythera$getModelDraw() {
        return epicfight$modelDraw;
    }

    @Unique @Override
    public boolean akythera$isVanillaModelHidden() {
        return akythera$vanillaModelHidden;
    }

    @Unique @Override
    public void akythera$setVanillaModelHidden(boolean vanillaModelHidden) {
        akythera$vanillaModelHidden = vanillaModelHidden;
    }

    @Unique @Override
    public boolean akythera$isArmaturePlaced() {
        return akythera$armaturePlaced;
    }

    @Unique @Override
    public void akythera$setArmaturePlaced(boolean armaturePlaced) {
        akythera$armaturePlaced = armaturePlaced;
    }

    @Unique @Override
    public ItemStackRenderState akythera$itemState(EquipmentSlot slot, ItemDisplayContext displayContext) {
        return epicfight$itemStates
            .computeIfAbsent(slot, _ -> new EnumMap<>(ItemDisplayContext.class))
            .computeIfAbsent(displayContext, _ -> new ItemStackRenderState());
    }
}
