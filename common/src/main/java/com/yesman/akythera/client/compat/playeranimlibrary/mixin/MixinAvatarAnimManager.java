package com.yesman.akythera.client.compat.playeranimlibrary.mixin;

import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.IMappedBone;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AvatarAnimManager.class)
public abstract class MixinAvatarAnimManager {
    @Inject(
        at = @At(value = "HEAD"),
        method = "updatePart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/zigythebird/playeranimcore/bones/PlayerAnimBone;)V"
    )
    public void updatePart(ModelPart part, PlayerAnimBone bone, CallbackInfo callbackInfo) {
        ((IMappedBone)bone).akythera$setModelPart(part);
    }
}
