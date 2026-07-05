package com.yesman.akythera.mixinaccessor.client;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.animation.keyframe.Pose;

public interface LivingEntityRenderStateAccessor {
    void akythera$setPose(Pose pose);

    Pose akythera$getPose();

    void akythera$setArmature(AssetAccessor<? extends Armature> pose);

    AssetAccessor<? extends Armature> akythera$getArmature();
}
