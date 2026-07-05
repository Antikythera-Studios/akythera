package com.yesman.akythera.client.api.event;


import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityPatchEvent;
import com.yesman.akythera.core.event.CancelableEventHook;
import com.yesman.akythera.core.event.EventHook;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityEventListener;

/// These are pre-defined hooks for all existing event types
///
/// To register an event listener that subscribes a specific event, call one of [EventHook#registerEvent],
/// [CancelableEventHook#registerCancelableEvent], or [CancelableEventHook#registerContextAwareEvent].
///
/// If you want to listen an event not globally, but per entity, call the exact same registering methods in
/// [EntityEventListener], which you can access by [EntityPatch#getEventListener] Be aware that you
/// only can register events that inherit [EntityPatchEvent] for per entity events.
///
/// For common event hooks for both client and server side, refer to [BuiltInEventHooks]
public final class BuiltInClientEventHooks {
    public static final class Entity {
        private Entity() {}
    }

    private BuiltInClientEventHooks() {}
}