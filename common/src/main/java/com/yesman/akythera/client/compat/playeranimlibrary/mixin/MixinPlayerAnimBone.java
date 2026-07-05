package com.yesman.akythera.client.compat.playeranimlibrary.mixin;

import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.IMappedBone;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = PlayerAnimBone.class)
public abstract class MixinPlayerAnimBone implements IMappedBone {
    @Unique
    private ModelPart lastUpdatedModelPart;

    @Override
    public void akythera$setModelPart(ModelPart modelPart) {
        lastUpdatedModelPart = modelPart;
    }

    @Override
    public ModelPart akythera$getModelPart() {
        return lastUpdatedModelPart;
    }
}
