package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.compat.IClientModPlugin;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranim.util.RenderUtil;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class PlayerAnimationLibraryPlugin implements IClientModPlugin {
    /// Poses `modelPart` by `bone`.
    ///
    /// @param modelBoneTransform the bone's forward-bound transform in the patch's model space, the armature's own
    ///                           transform applied
    public static void mapAnimation(
        @NonNull final Bone bone,
        @Nullable final Bone bendBone,
        @NonNull final Pose pose,
        @NonNull final Matrix4f modelBoneTransform,
        @NonNull PlayerAnimBone palBone,
        @NonNull ModelPart modelPart
    ) {
        PartPose initPose = modelPart.getInitialPose();
        // Calculates an initial pose of a cube
        Matrix4f initPoseTransform = new Matrix4f();
        initPoseTransform.translate(initPose.x(), initPose.y(), initPose.z());
        initPoseTransform.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(1, 0, 0), initPose.xRot()));
        initPoseTransform.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(0, 1, 0), initPose.yRot()));
        initPoseTransform.rotate(new Quaternionf().fromAxisAngleDeg(new Vector3f(0, 0, 1), initPose.zRot()));
        initPoseTransform.scale(initPose.xScale(), initPose.yScale(), initPose.zScale());

        Matrix4f transform = new Matrix4f();
        // Revert Mesh space coord -> Minecraft model space coord
        transform.scale(-16.0F, -16.0F, 16.0F);
        transform.translate(0.0F, -1.501F, 0.0F);

        // Apply animation in mesh space
        transform.mul(modelBoneTransform);
        transform.mul(bone.getInverseTransformToOrigin());

        // Minecraft model space coord -> Mesh space coord (where the center of each leg becomes [0, 0, 0])
        // Was specified in LivingEntityRenderer#submit (v26.1, hardcoded)
        transform.translate(0.0F, 1.501F, 0.0F);
        transform.scale(-1.0F / 16.0F, -1.0F / 16.0F, 1.0F / 16.0F);

        // Move the cube to initial pose
        transform.mul(initPoseTransform);

        Vector3f translation = Matrix4fUtils.extractTranslation(transform);
        Vector3f eulerZXY = Matrix4fUtils.extractRotator(transform).getEulerAnglesZYX(new Vector3f());
        Vector3f scale = Matrix4fUtils.extractScale(transform);

        /* mapping the animation into vanilla model part*/
        /*
        modelPart.x = translation.x();
        modelPart.y = translation.y();
        modelPart.z = translation.z();
        modelPart.xRot = eulerZXY.x();
        modelPart.yRot = eulerZXY.y();
        modelPart.zRot = eulerZXY.z();
        modelPart.xScale = scale.x();
        modelPart.yScale = scale.y();
        modelPart.zScale = scale.z();
        */

        palBone.position.x = translation.x() - initPose.x();
        palBone.position.y = -translation.y() + initPose.y();
        palBone.position.z = translation.z() - initPose.z();
        palBone.rotation.x = eulerZXY.x();
        palBone.rotation.y = eulerZXY.y() - initPose.yRot();
        palBone.rotation.z = eulerZXY.z();
        palBone.scale.x = scale.x();
        palBone.scale.y = scale.y();
        palBone.scale.z = scale.z();

        RenderUtil.translatePartToBone(modelPart, palBone, initPose);

        // Apply bends from bend bone
        if (bendBone != null && pose.hasTransform(bendBone.getName())) {
            Vector3d rotations = pose.get(bendBone.getName()).rot().getEulerAnglesZYX(new Vector3d());
            palBone.bend = (float)-rotations.x();
        }
    }

    public static void bindPreviewAnimManager(final LivingEntityRenderState renderState, final AnimatableObject animatableObject) {
        if (renderState instanceof IAvatarAnimationState avatarAnimationState) {
            // Add a headless anim manager, this would cause crash under the premise of animation-only usage
            AvatarAnimManager avatarAnimManager = new AvatarAnimManager(null);
            AkytheraAnimationLayer locomotionLayer = new AkytheraAnimationLayer(animatableObject);
            avatarAnimManager.addAnimLayer(0, locomotionLayer);
            avatarAnimationState.playerAnimLib$setAnimManager(avatarAnimManager);
        }
    }

    private static final Identifier LOCOMOTION_LAYER = Identifier.fromNamespaceAndPath(Akythera.MODID, "animation_layer");

    @Override
    public void onInitializeClient() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LOCOMOTION_LAYER, 1000, player -> {
            EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(player);

            if (entityPatch != null && entityPatch.getArmatureComponent() != null) {
                return new AkytheraAnimationLayer(entityPatch);
            }

            return null;
        });
    }
}
