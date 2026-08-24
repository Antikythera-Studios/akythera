package com.yesman.akythera.client.compat.playeranimlibrary.mixin;

import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.INamedModelPart;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.ManagedModel;
import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.accessors.IBoneUpdater;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranim.util.RenderUtil;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/// Mirrored after [com.zigythebird.playeranim.mixin.PlayerModelMixin]

@Mixin(value = Model.class, priority = 2000)
public abstract class MixinModel implements ManagedModel, IBoneUpdater {
    /// Saves model parts by names, to map EF animation based on [ModelPart#getInitialPose]
    @Unique
    private final Map<String, ModelPart> akythera$modelPartsByName = new HashMap<>();

    @Unique
    private final Map<ModelPart, PlayerAnimBone> akythera$animBones = new HashMap<>();

    @Override @Unique
    public ModelPart akythera$getModelPartByName(String partName) {
        return akythera$modelPartsByName.get(partName);
    }

    @Override @Unique
    public void akythera$addPart(String partName, ModelPart modelPart) {
        akythera$modelPartsByName.put(partName, modelPart);
        akythera$animBones.put(modelPart, new PlayerAnimBone(partName));
    }

    @Inject(at = @At(value = "RETURN"), method = "<init>(Lnet/minecraft/client/model/geom/ModelPart;Ljava/util/function/Function;)V")
    public void akythera$init(ModelPart root, Function<Identifier, RenderType> renderType, CallbackInfo callbackInf) {
        ((INamedModelPart)(Object)root).akythera$setChildNameRecursively(this);
    }

    @Inject(
        at = {
            @At("RETURN")
        },
        method = "setupAnim(Ljava/lang/Object;)V"
    )
    private void setupEntityAnimation(Object state, CallbackInfo ci) {
        // Player model is controlled by PAL. We only in charge on non-player entities.
        if ((Object)this instanceof PlayerModel) return;

        if (state instanceof IAvatarAnimationState avatarAnimationState && avatarAnimationState.playerAnimLib$getAnimManager() != null) {
            AvatarAnimManager emote = avatarAnimationState.playerAnimLib$getAnimManager();

            if (emote.isActive()) {
                // Map bone transform -> model part transform to play animation
                for (Map.Entry<ModelPart, PlayerAnimBone> e : akythera$animBones.entrySet()) {
                    pal$updatePart(emote, e.getKey(), e.getValue());
                }
            } else {
                pal$resetAll(emote);
            }
        } else {
            pal$resetAll(null);
        }
    }

    @Override @Unique
    public void pal$updatePart(AvatarAnimManager emote, ModelPart part, PlayerAnimBone bone) {
        RenderUtil.copyVanillaPart(part, bone);
        emote.updatePart(part, bone);
    }
}
