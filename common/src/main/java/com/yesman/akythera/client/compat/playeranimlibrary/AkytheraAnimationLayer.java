package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.api.animation.ArmatureComponent;
import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.IMappedBone;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.INamedModelPart;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public final class AkytheraAnimationLayer implements IAnimation {
    private final ArmatureComponent armatureComponent;
    private final Matrix4f[] cachedBoundPose;
    private Pose cachedPose;

    public AkytheraAnimationLayer(final AnimatableObject animatableObject) {
        // This null check should be conducted before creating animation layer
        assert animatableObject.getArmatureComponent() != null;
        armatureComponent = animatableObject.getArmatureComponent();
        cachedBoundPose = Matrix4fUtils.allocateMatrixArray(armatureComponent.getArmature().get().getBoneCount());
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void setupAnim(AnimationData state) {
        updatePose();
    }

    @Override
    public void get3DTransform(@NotNull PlayerAnimBone playerAnimBone) {
        ModelPart modelPart = ((IMappedBone)playerAnimBone).akythera$getModelPart();

        if (modelPart != null) {
            Armature armature = armatureComponent.getArmature().get();
            INamedModelPart namedModelPart = ((INamedModelPart)(Object)modelPart);
            Bone bone = armature.getBoneByName(namedModelPart.akythera$getName());
            Bone bendBone = armature.getBoneByName(namedModelPart.akythera$getName() + "_bend");

            if (bone != null) {
                PlayerAnimationLibraryCompatibility.mapAnimation(bone, bendBone, cachedPose, cachedBoundPose[bone.getId()], playerAnimBone, modelPart);
            }
        }
    }

    /// Opened animation setup for manual update (e.g. preview model pose setup)
    public void updatePose() {
        cachedPose = armatureComponent.getAnimator().getPose();
        cachedPose.boundTransformRecursively(
            armatureComponent.getArmature().get().getRootBone(),
            new Matrix4f(),
            cachedBoundPose
        );

        if (armatureComponent.getOwner() instanceof EntityPatch<?> entityPatch) {
            entityPatch.getOwner().setYBodyRot(entityPatch.getOwner().getYRot());
        }
    }
}