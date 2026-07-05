package com.yesman.akythera.core.event.subscription;

import com.yesman.akythera.core.event.Event;
import com.yesman.akythera.core.event.EventContext;

/// [DefaultEventSubscription] for common event firing rule that doesn't fire lower prioritized
/// events if it's canceled by higher event.
///
/// [ContextAwareEventSubscription] for events that still fired if it's canceled by higher
/// prioritized events, with receving a [EventContext] as parameter to decide whether fire
/// or cancel the event by custom logics
public interface EventSubscriptionType<T extends Event> {
}
