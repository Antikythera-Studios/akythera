package com.yesman.akythera.neoforged.event;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = Akythera.MODID)
public final class EntityEvents {
    @SubscribeEvent
    public static void entityJoinLevel(final EntityJoinLevelEvent event) {
        AkytheraPatches.getEntityPatchOptional(event.getEntity()).ifPresent(entityPatch -> {
            BuiltInEventHooks.Entity.SPAWNED.postWithListener(
                new EntitySpawnEvent(entityPatch, event.getLevel(), event.loadedFromDisk()),
                entityPatch.getEntityEventListener()
            );
        });
    }
}
