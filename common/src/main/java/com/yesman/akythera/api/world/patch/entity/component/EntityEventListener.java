package com.yesman.akythera.api.world.patch.entity.component;


import com.yesman.akythera.api.event.entity.EntityPatchEvent;
import com.yesman.akythera.core.event.*;
import com.yesman.akythera.core.util.IdentifierProvider;
import com.yesman.akythera.core.event.subscription.ContextAwareEventSubscription;
import com.yesman.akythera.core.event.subscription.DefaultEventSubscription;
import com.yesman.akythera.core.event.subscription.EventSubscriptionType;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Set of Event listeners owned by [EntityPatch]
///
/// Unlike the event subscribers that is applied globally and permanent, you can add and remove
/// listeners dynamically in runtime by this module.
///
/// All events are required a subscriber identifier to be registered, which is used when
/// removing existing subscribers
///
/// See [EventSubscriptionType] for the detailed explanation about each subscriptionType type
public final class EntityEventListener {
    private final EntityPatch<?> entityPatch;
    private final Map<EventHook<? extends EntityPatchEvent<?>>, List<EventListener<? extends EntityPatchEvent<?>>>> eventListeners;

    public EntityEventListener(EntityPatch<?> entityPatch) {
        this.entityPatch = entityPatch;
        eventListeners = new HashMap<> ();
    }

    // For deep copy
    public EntityEventListener(EntityPatch<?> entityPatch, final EntityEventListener oldListener) {
        this.entityPatch = entityPatch;
        eventListeners = oldListener.eventListeners;
    }

    /// Returns subscribers for given even
    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T extends Event> Stream<EventListener<T>> getListenersFor(EventHook<T> event) {
        return eventListeners.containsKey(event) ? eventListeners.get(event).stream().map(subscriber -> (EventListener<T>)subscriber) : Stream.empty();
    }

    /// Registers an event with a default priority, which lives permanently on the owner's lifecycle.
    public <T extends EntityPatchEvent<?>> void registerEvent(EventHook<T> event, DefaultEventSubscription<T> subscription) {
        registerEvent(event, subscription, IdentifierProvider.permanent(), 0);
    }

    /// Registers an event with default priority
    /// @param identifier specify the subscriber identifier to be referenced by other events, it will be stored at [EventContext]
    ///                   use this subscriber identifier to remove the listener
    public <T extends EntityPatchEvent<?>> void registerEvent(EventHook<T> event, DefaultEventSubscription<T> subscription, IdentifierProvider identifier) {
        registerEvent(event, subscription, identifier, 0);
    }

    /// Registers an event with full parameters
    public <T extends EntityPatchEvent<?>> void registerEvent(EventHook<T> event, DefaultEventSubscription<T> subscription, IdentifierProvider identifier, int priority) {
        if (event.logicalSide().isEntityOnValidSide(entityPatch.getOriginal())) {
            var subscriberList = eventListeners.computeIfAbsent(event, key -> new ArrayList<> ());
            subscriberList.add(new EventListener<> (identifier.getStringId(), priority, subscription));
        }
    }

    /// Registers an event with a default priority, which lives permanently on the owner's lifecycle.
    public <T extends EntityPatchEvent<?> & CancelableEvent> void registerContextAwareEvent(EventHook<T> event, ContextAwareEventSubscription<T> subscription) {
        registerContextAwareEvent(event, subscription, IdentifierProvider.permanent(), 0);
    }

    /// Registers an event with default priority
    /// @param identifier specify the subscriber identifier to be referenced by other events, it will be stored at [EventContext]
    ///                   use this subscriber identifier to remove the listener
    public <T extends EntityPatchEvent<?> & CancelableEvent> void registerContextAwareEvent(EventHook<T> event, ContextAwareEventSubscription<T> subscription, IdentifierProvider identifier) {
        registerContextAwareEvent(event, subscription, identifier, 0);
    }

    /// Registers an event with full parameters
    public <T extends EntityPatchEvent<?> & CancelableEvent> void registerContextAwareEvent(EventHook<T> event, ContextAwareEventSubscription<T> subscription, IdentifierProvider identifier, int priority) {
        if (event.logicalSide().isEntityOnValidSide(entityPatch.getOriginal())) {
            var subscriberList = eventListeners.computeIfAbsent(event, key -> new ArrayList<>());
            subscriberList.add(new EventListener<> (identifier.getStringId(), priority, subscription));
        }
    }

    /// Removes all listeners matching with the given identifier
    public void removeListenersBelongTo(IdentifierProvider identifier) {
        if (IdentifierProvider.permanent().equals(identifier)) {
            throw new IllegalArgumentException("Can't remove permanent listeners");
        }

        // remove matching subscribers with given identifier
        eventListeners.values().forEach(subscriberList -> subscriberList.removeIf(subscriber -> subscriber.identifier().equals(identifier.getStringId())));

        // remove empty events
        eventListeners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /// Returns the owner
    public EntityPatch<?> getEntityPatch() {
        return entityPatch;
    }
}
