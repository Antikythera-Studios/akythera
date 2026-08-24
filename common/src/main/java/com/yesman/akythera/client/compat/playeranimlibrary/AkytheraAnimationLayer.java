package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.IMappedBone;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.INamedModelPart;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.NotNull;

public final class AkytheraAnimationLayer implements IAnimation {
    private final ArmatureComponent armatureComponent;

    public AkytheraAnimationLayer(final AnimatableObject animatableObject) {
        // This null check should be conducted before creating animation layer
        assert animatableObject.getArmatureComponent() != null;
        armatureComponent = animatableObject.getArmatureComponent();
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void setupAnim(AnimationData state) {
        if (armatureComponent.getOwner() instanceof EntityPatch<?> entityPatch) {
            entityPatch.getOriginal().setYBodyRot(entityPatch.getOriginal().getYRot());
        }
    }

    @Override
    public void get3DTransform(@NotNull PlayerAnimBone playerAnimBone) {
        ModelPart modelPart = ((IMappedBone)playerAnimBone).akythera$getModelPart();

        if (modelPart != null) {
            Armature armature = armatureComponent.getArmature().get();
            INamedModelPart namedModelPart = ((INamedModelPart)(Object)modelPart);
            Bone bone = armature.getBoneByName(namedModelPart.akythera$getName());

            if (bone != null && armatureComponent.getBoundTransforms() != null) { // `armatureComponent.getBoundTransforms()` is null in the first frame
                Bone bendBone = armature.getBoneByName(namedModelPart.akythera$getName() + "_bend");
                PlayerAnimationLibraryPlugin.mapAnimation(bone, bendBone, armatureComponent.getCachedPose(), armatureComponent.getBoundTransforms()[bone.getId()], playerAnimBone, modelPart);
            }
        }
    }
}