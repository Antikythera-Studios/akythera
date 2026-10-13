package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.impl.PALPoseBinder;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.IMappedBone;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.INamedModelPart;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;

/// Poses a vanilla model by the armature the [AnimatableObject] answers, read on every call.
public final class AkytheraAnimationLayer implements IAnimation {
    private final AnimatableObject animatableObject;

    public AkytheraAnimationLayer(final AnimatableObject animatableObject) {
        this.animatableObject = animatableObject;
    }

    /// PAL poses the vanilla model while the armature holds a switched-on [PALPoseBinder].
    @Override
    public boolean isActive() {
        ArmatureComponent armatureComponent = animatableObject.getArmatureComponent();

        return armatureComponent != null && armatureComponent.getComponent(PALPoseBinder.class) != null;
    }

    @Override
    public void setupAnim(AnimationData state) {
        if (animatableObject instanceof EntityPatch<?> entityPatch) {
            entityPatch.getOriginal().setYBodyRot(entityPatch.getOriginal().getYRot());
        }
    }

    @Override
    public void get3DTransform(@NonNull PlayerAnimBone playerAnimBone) {
        ModelPart modelPart = ((IMappedBone)playerAnimBone).akythera$getModelPart();
        ArmatureComponent armatureComponent = animatableObject.getArmatureComponent();

        if (modelPart != null && armatureComponent != null) {
            Armature armature = armatureComponent.getArmature().get();
            INamedModelPart namedModelPart = ((INamedModelPart)(Object)modelPart);
            Bone bone = armature.getBoneByName(namedModelPart.akythera$getName());

            if (bone != null && armatureComponent.getBoundTransforms() != null) { // `armatureComponent.getBoundTransforms()` is null in the first frame
                Bone bendBone = armature.getBoneByName(namedModelPart.akythera$getName() + "_bend");
                Matrix4f modelBoneTransform = armatureComponent.modelTransform().toMatrix().mul(armatureComponent.getBoundTransforms()[bone.getId()]);

                PlayerAnimationLibraryPlugin.mapAnimation(bone, bendBone, armatureComponent.getCachedPose(), modelBoneTransform, playerAnimBone, modelPart);
            }
        }
    }
}