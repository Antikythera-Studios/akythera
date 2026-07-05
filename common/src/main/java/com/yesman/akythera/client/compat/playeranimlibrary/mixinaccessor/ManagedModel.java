package com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/// A [Model] that manages its parts by name
public interface ManagedModel {
    ModelPart akythera$getModelPartByName(String partName);

    void akythera$addPart(String partName, ModelPart modelPart);
}
