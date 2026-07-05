package com.yesman.akythera.core.blueprint.script;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import net.minecraft.world.level.Level;

import java.util.List;

public interface EntityPatchScript {
    /// Called on the constructor of [EntityPatch]
    void onConstruct();

    /// Called on [EntityPatch#onJoinWorld]
    void onJoinWorld(final Level level);

    /// Called on [EntityPatch#postTick()]
    void update();

    /// List of components to attach to the owner
    List<ComponentBlueprint<?, ?>> componentsToAttach();
}
