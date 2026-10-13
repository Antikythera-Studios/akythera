package com.yesman.akythera.api.event;

import com.yesman.akythera.api.event.entity.*;
import com.yesman.akythera.api.event.entity.animation.EndAnimMontageEvent;
import com.yesman.akythera.api.event.entity.animation.InitBlendingAnimatorEvent;
import com.yesman.akythera.api.event.entity.animation.PlayAnimMontageEvent;
import com.yesman.akythera.api.event.registry.GatherScriptModuleEvent;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import com.yesman.akythera.api.event.registry.RegistryEntryLookupEvent;
import com.yesman.akythera.core.event.CancelableEventHook;
import com.yesman.akythera.core.event.EventHook;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.api.world.patch.entity.component.EntityEventListener;

/// These are pre-defined hooks for all existing event types
///
/// To register an event listener that subscribes a specific event, call one of
/// [EventHook#registerEvent] or [CancelableEventHook#registerContextAwareEvent].
///
/// Use [EntityEventListener] to listen events in one entity's bound.
///
/// Addon developers inherit [EntityPatchEvent] for per entity events, and call
/// it by [EventHook#postWithListener].
public final class BuiltInEventHooks {
    public static final class Animation {
        public static final EventHook<PlayAnimMontageEvent> BEGIN = EventHook.createEventHook();
        public static final EventHook<EndAnimMontageEvent> END = EventHook.createEventHook();
        public static final EventHook<InitBlendingAnimatorEvent> INIT_BLENDING_ANIMATOR = EventHook.createEventHook();

        private Animation() {}
    }

    public static final class Entity {
        public static final EventHook<EntityConstructEvent> CONSTRUCTED = EventHook.createEventHook();
        public static final EventHook<EntitySpawnEvent> SPAWNED = EventHook.createEventHook();
        public static final EventHook<EntityTrackingEvent.Start> TRACKING_START = EventHook.createSidedEventHook(LogicalSide.SERVER);
        public static final EventHook<EntityTrackingEvent.Stop> TRACKING_END = EventHook.createSidedEventHook(LogicalSide.SERVER);
        public static final EventHook<TickEvent.CommonPre> TICK_PRE = EventHook.createEventHook();
        public static final EventHook<TickEvent.CommonPost> TICK_POST = EventHook.createEventHook();
        public static final EventHook<TickEvent.ClientPre> CLIENT_TICK_PRE = EventHook.createSidedEventHook(LogicalSide.CLIENT);
        public static final EventHook<TickEvent.ClientPost> CLIENT_TICK_POST = EventHook.createSidedEventHook(LogicalSide.CLIENT);
        public static final EventHook<TickEvent.RenderTick> RENDER_TICK = EventHook.createSidedEventHook(LogicalSide.CLIENT);
        public static final EventHook<TickEvent.ServerPre> SERVER_TICK_PRE = EventHook.createSidedEventHook(LogicalSide.SERVER);
        public static final EventHook<TickEvent.ServerPost> SERVER_TICK_POST = EventHook.createSidedEventHook(LogicalSide.SERVER);
        public static final EventHook<SendUpdateDataEvent> SEND_SYNC_DATA = EventHook.createSidedEventHook(LogicalSide.SERVER);
        public static final EventHook<HandlePersistentDataEvent.Serialize> SERIALIZE = EventHook.createSidedEventHook(LogicalSide.SERVER);
        public static final EventHook<HandlePersistentDataEvent.Deserialize> DESERIALIZE = EventHook.createSidedEventHook(LogicalSide.SERVER);

        private Entity() {}
    }

    public static final class Registry {
        public static final EventHook<RegistryAccessEvent.Load> REGISTRY_ACCESS_LOAD = EventHook.createEventHook();
        public static final EventHook<RegistryAccessEvent.Unload> REGISTRY_ACCESS_UNLOAD = EventHook.createEventHook();
        public static final EventHook<GatherScriptModuleEvent> GATHER_SCRIPT_MODULE = EventHook.createEventHook();
        public static final CancelableEventHook<RegistryEntryLookupEvent.ByKey<?>> LOOKUP_ENTRY_BY_KEY = CancelableEventHook.createCancelableEventHook();
        public static final CancelableEventHook<RegistryEntryLookupEvent.ByValue<?>> LOOKUP_ENTRY_BY_VALUE = CancelableEventHook.createCancelableEventHook();

        private Registry() {}
    }

    private BuiltInEventHooks() {}
}
