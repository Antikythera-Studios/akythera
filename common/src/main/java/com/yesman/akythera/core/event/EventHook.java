package com.yesman.akythera.core.event;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityPatchEvent;
import com.yesman.akythera.client.api.event.BuiltInClientEventHooks;
import com.yesman.akythera.core.event.subscription.DefaultEventSubscription;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.component.EntityEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

/// An event bus that is dedicated to one [Event] type
///
/// Note that this object isn't created each time an event is fired. it
/// only defines event type and its subscriptions. Look at [Event]
/// which hold event states and arguments
///
/// To create custom events, follow these codebase: [BuiltInEventHooks]
/// and [BuiltInClientEventHooks] for client-side only events
public class EventHook<T extends Event> {
	/// Subscribers grouped by priority in descending order
	final TreeMap<Integer, List<EventListener<T>>> subscribers = new TreeMap<> ((i1, i2) -> Integer.compare(i2, i1));

    /// Determines if the event is only called in logical side
    protected final LogicalSide logicalSide;

    protected EventHook(LogicalSide logicalSide) {
        this.logicalSide = logicalSide;
    }

    /// Post the event to subscribers and execute tasks by their priority in descending order
    public T post(T event) {
        for (List<EventListener<T>> tier : this.subscribers.values()) {
            for (EventListener<T> subscriber : tier) {
                if (subscriber.subscriptionType() instanceof DefaultEventSubscription<T> passiveSubscription) {
                    passiveSubscription.fire(event);
                }
            }
        }

        return event;
    }

    /// Post the event to subscribers including from [EntityEventListener], and execute tasks by their priority in descending order
    public T postWithListener(T event, EntityEventListener eventListener) {
        if (!(event instanceof EntityPatchEvent)) {
            throw new IllegalArgumentException("EventHook instance must be a subtype of LivingEntityPatchEvent to be posted with EntityEventListener");
        }

        Stream.concat(this.subscribers.values().stream().flatMap(List::stream), eventListener.getListenersFor(this))
            .sorted((o1, o2) -> Integer.compare(o2.priority(), o1.priority()))
            .forEach(subscriber -> {
                if (subscriber.subscriptionType() instanceof DefaultEventSubscription<T> passiveSubscription) {
                    passiveSubscription.fire(event);
                }
            });

        return event;
    }

	/// Registers an event with default identifier and priority
	public void registerEvent(DefaultEventSubscription<T> subscription) {
		this.registerEvent(subscription, getDefaultSubscriberName(), 0);
	}
	
	/// Registers an event with default identifier
	/// @param priority determines the order of the event in descending order
	public void registerEvent(DefaultEventSubscription<T> subscription, int priority) {
		this.registerEvent(subscription, getDefaultSubscriberName(), priority);
	}
	
	/// Registers an event with default priority
	/// @param name specify the subscriber identifier to be referenced by other events, it will be stored at {@link EventContext}
	public void registerEvent(DefaultEventSubscription<T> subscription, String name) {
		this.registerEvent(subscription, name, 0);
	}
	
	/// Registers an event with full parameters
	public void registerEvent(DefaultEventSubscription<T> subscription, String name, int priority) {
		this.subscribers.computeIfAbsent(priority, _ -> new ArrayList<>()).add(new EventListener<> (name, priority, subscription));
	}

    /// Returns which side the event hook should be posted
    public final LogicalSide logicalSide() {
        return this.logicalSide;
    }

	/// Returns a class identifier who called register_event methods
	protected static String getDefaultSubscriberName() {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stackTraceElements[2];
        return caller.getClassName();
	}
	
	/// Defines an event hook
	public static <T extends Event> EventHook<T> createEventHook() {
		return new EventHook<> (LogicalSide.BOTH);
	}

    /// Defines an event hook only works in one side.
    ///
    /// Event side is automatically filtered only for [EntityPatchEvent] by [EntityPatch#isLogicalClient].
    /// For normal event's posting, the side checking should be conducted before.
    public static <T extends Event> EventHook<T> createSidedEventHook(LogicalSide logicalSide) {
        return new EventHook<> (logicalSide);
    }
}
