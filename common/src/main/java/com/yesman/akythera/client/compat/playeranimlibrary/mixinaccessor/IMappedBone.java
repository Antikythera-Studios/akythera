package com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor;

import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;

/// Extender of [PlayerAnimBone] as PAL maps [ModelPart] 1:1 so far
public interface IMappedBone {
    /// Map recently updated model part
    void akythera$setModelPart(ModelPart modelPart);

    /// Get recently updated model part
    ModelPart akythera$getModelPart();
}
