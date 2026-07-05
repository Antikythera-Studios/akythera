package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class MixinLivingEntityRenderState implements LivingEntityRenderStateAccessor {
    @Unique @Nullable
    private Pose epicfight$pose;

    @Unique @Nullable
    private AssetAccessor<? extends Armature> epicfight$armature;

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
}
