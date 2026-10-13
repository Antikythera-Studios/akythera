package com.yesman.akythera.api.event.registry;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.event.CancelableEvent;
import com.yesman.akythera.core.event.Event;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/// Called when a [ScriptType] codec looks up a data pack registry entry, before [StaticRegistryAccess]
/// is asked. A subscriber that owns the entry answers the lookup, which skips the later subscribers.
public abstract class RegistryEntryLookupEvent extends Event implements CancelableEvent {
    /// Looks up the entry a key names.
    public static final class ByKey<T> extends RegistryEntryLookupEvent {
        private final ResourceKey<T> key;
        private @Nullable Holder<T> result;

        public ByKey(final ResourceKey<T> key) {
            this.key = key;
        }

        public ResourceKey<T> getKey() {
            return key;
        }

        /// The answered entry, or null when no subscriber owns it.
        public @Nullable Holder<T> getResult() {
            return result;
        }

        /// Answers the lookup.
        public void setResult(final Holder<T> result) {
            this.result = result;
            cancel();
        }
    }

    /// Looks up the key of a registry entry.
    public static final class ByValue<T> extends RegistryEntryLookupEvent {
        private final ResourceKey<? extends Registry<T>> registry;
        private final T value;
        private @Nullable ResourceKey<T> result;

        public ByValue(final ResourceKey<? extends Registry<T>> registry, final T value) {
            this.registry = registry;
            this.value = value;
        }

        public ResourceKey<? extends Registry<T>> getRegistry() {
            return registry;
        }

        public T getValue() {
            return value;
        }

        /// The answered key, or null when no subscriber owns the entry.
        public @Nullable ResourceKey<T> getResult() {
            return result;
        }

        /// Answers the lookup.
        public void setResult(final ResourceKey<T> result) {
            this.result = result;
            cancel();
        }
    }
}
