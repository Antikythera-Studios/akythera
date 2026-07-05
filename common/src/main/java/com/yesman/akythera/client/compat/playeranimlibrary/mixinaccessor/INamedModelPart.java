package com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/// [ModelPart] mixin accessor that saves part name
public interface INamedModelPart {
    String akythera$getName();

    void akythera$setName(ManagedModel managedModel, String name);

    /// @param managedModel owner [Model] of this model part
    void akythera$setChildNameRecursively(ManagedModel managedModel);
}
