package com.yesman.akythera.core.event.subscription;

import com.yesman.akythera.core.event.CancelableEvent;
import com.yesman.akythera.core.event.Event;
import com.yesman.akythera.core.event.EventContext;

/// A subscriptionType type that developers can inspect event, cancel history by [EventContext]
/// This event subscriptionType type called even after the event is canceled
@FunctionalInterface
public interface ContextAwareEventSubscription<T extends Event & CancelableEvent> extends EventSubscriptionType<T> {
	void fire(T event, EventContext eventContext);
}
