package com.yesman.akythera.client.compat.playeranimlibrary.mixin;

import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.INamedModelPart;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.ManagedModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(ModelPart.class)
public abstract class MixinModelPart implements INamedModelPart {
    @Unique
    private String epicfight$name;

    @Shadow
    private Map<String, ModelPart> children;

    @Override @Unique
    public String akythera$getName() {
        return epicfight$name;
    }

    @Override @Unique
    public void akythera$setName(ManagedModel managedModel, String name) {
        epicfight$name = name;
        managedModel.akythera$addPart(name, (ModelPart)(Object)this);
    }

    @Override @Unique
    public void akythera$setChildNameRecursively(ManagedModel managedModel) {
        for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
            ((INamedModelPart)(Object)entry.getValue()).akythera$setName(managedModel, entry.getKey());
            ((INamedModelPart)(Object)entry.getValue()).akythera$setChildNameRecursively(managedModel);
        }
    }
}
