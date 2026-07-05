package com.yesman.akythera.core.event.subscription;

import com.yesman.akythera.core.event.Event;

/// A default event subscriptionType type which is not cancelable, and interruptible by higher prioritized events
@FunctionalInterface
public interface DefaultEventSubscription<T extends Event> extends EventSubscriptionType<T> {
	void fire(T event);
}
