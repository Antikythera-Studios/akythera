package com.yesman.akythera.neoforged.event;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntitySpawnEvent;
import com.yesman.akythera.api.event.entity.EntityTrackingEvent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Akythera.MODID)
public final class EntityEvents {
    @SubscribeEvent
    public static void startTracking(final PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer tracker)) return;

        AkytheraPatches.getEntityPatchOptional(event.getTarget()).ifPresent(entityPatch ->
            BuiltInEventHooks.Entity.TRACKING_START.postWithListener(
                new EntityTrackingEvent.Start(entityPatch, tracker),
                entityPatch.getEntityEventListener()
            )
        );
    }

    @SubscribeEvent
    public static void stopTracking(final PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer tracker)) return;

        AkytheraPatches.getEntityPatchOptional(event.getTarget()).ifPresent(entityPatch ->
            BuiltInEventHooks.Entity.TRACKING_END.postWithListener(
                new EntityTrackingEvent.Stop(entityPatch, tracker),
                entityPatch.getEntityEventListener()
            )
        );
    }

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
