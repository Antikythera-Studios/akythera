package com.yesman.akythera.client.input;

import com.yesman.akythera.api.data.modularize.node.ScriptCallback;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.input.TriggeringState;
import com.yesman.akythera.api.input.TriggeringType;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.mixinaccessor.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Client-side registry of [InputAction] listeners, dispatched once per tick by
/// [com.yesman.akythera.mixin.client.MixinMinecraft].
///
/// Actions are keyed by [Holder], so two data pack entries carrying the same key and timing stay
/// separate bindings.
@ClientOnly
public final class InputManager {
    private static final InputManager INSTANCE = new InputManager();

    public static InputManager getInstance() {
        return INSTANCE;
    }

    /// Listeners per bound action, in registration order.
    private final Map<Holder<InputAction>, List<InputListener>> listeners = new HashMap<>();

    /// The key each bound action resolved to, which is what releases it again.
    private final Map<Holder<InputAction>, KeyMapping> boundKeys = new HashMap<>();

    /// Key mappings with at least one binding; the per-tick driver scans only these.
    private final Set<KeyMapping> boundKeyMappings = new HashSet<>();

    /// The listener [#bindAction] registered per action, so a rebind or [#unbindAction] releases the same one.
    private final Map<Holder<InputAction>, InputListener> actionBindings = new HashMap<>();

    private InputManager() {}

    /// Releases every [#bindAction] binding when the client leaves the world it bound them in.
    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(event -> INSTANCE.unbindActions());
    }

    /// Runs the callbacks when `action` fires, replacing the ones an earlier call bound to it. A callback runs as
    /// the patch of [Minecraft#getCameraEntity], or as built when that entity has none. Does nothing for a key
    /// mapping no mod registered.
    ///
    /// @param started   the first [TriggeringState#TRIGGERED] of a hold, or every one for an action that is not held
    /// @param triggered every [TriggeringState#TRIGGERED]
    /// @param completed every [TriggeringState#COMPLETED]
    /// @param canceled  every [TriggeringState#CANCELED]
    public void bindAction(
        final InputAction action,
        final @Nullable ScriptCallback started,
        final @Nullable ScriptCallback triggered,
        final @Nullable ScriptCallback completed,
        final @Nullable ScriptCallback canceled
    ) {
        unbindAction(action);

        KeyMapping keyMapping = KeyMapping.get(action.keyMapping());

        if (keyMapping == null) {
            Akythera.LOGGER.warn("No key mapping is registered as {}", action.keyMapping());

            return;
        }

        Holder<InputAction> holder = holderOf(action);
        InputListener listener = new CallbackListener(started, triggered, completed, canceled);

        register(holder, keyMapping, listener);
        actionBindings.put(holder, listener);
    }

    /// Releases the callbacks [#bindAction] bound to `action`.
    public void unbindAction(final InputAction action) {
        Holder<InputAction> holder = holderOf(action);
        InputListener listener = actionBindings.remove(holder);

        if (listener != null) unregister(holder, listener);
    }

    /// Starts or stops `action` suppressing its key, as [InputAction#suppressLowerActions] does: the actions of a
    /// lower priority on the key never trigger, and vanilla never reads it. Lasts until `action` is unbound. Does
    /// nothing for an action that is not bound.
    public void suppressInputKey(final InputAction action, final boolean suppress) {
        Holder<InputAction> holder = holderOf(action);
        KeyMapping keyMapping = boundKeys.get(holder);

        if (keyMapping != null) ((KeyMappingAccessor) keyMapping).akythera$suppressAction(holder, suppress);
    }

    private void unbindActions() {
        actionBindings.forEach(this::unregister);
        actionBindings.clear();
    }

    private static Holder<InputAction> holderOf(final InputAction action) {
        return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.INPUT_ACTION_KEY).wrapAsHolder(action);
    }

    /// Adds `listener` to `action`, binding it to `keyMapping` on the first listener.
    public void register(
        final Holder<InputAction> action,
        final KeyMapping keyMapping,
        final InputListener listener
    ) {
        boolean firstListener = !listeners.containsKey(action);

        listeners.computeIfAbsent(action, bound -> new ArrayList<>()).add(listener);

        if (!firstListener) return;

        boundKeys.put(action, keyMapping);
        ((KeyMappingAccessor) keyMapping).akythera$bindAction(action);
        boundKeyMappings.add(keyMapping);
    }

    /// Drops one `listener` from `action`, releasing the key once the last one is gone.
    public void unregister(final Holder<InputAction> action, final InputListener listener) {
        List<InputListener> bound = listeners.get(action);

        if (bound == null || !bound.remove(listener) || !bound.isEmpty()) return;

        listeners.remove(action);

        KeyMapping keyMapping = boundKeys.remove(action);

        if (keyMapping == null) return;

        if (((KeyMappingAccessor) keyMapping).akythera$unbindAction(action)) {
            boundKeyMappings.remove(keyMapping);
        }
    }

    /// Runs the [TriggeringType] timing table of every bound key mapping once. Iterates
    /// a copy, since a listener may bind or unbind a key meanwhile.
    @ApiStatus.Internal
    public void dispatchInputActions() {
        for (KeyMapping keyMapping : Set.copyOf(boundKeyMappings)) {
            ((KeyMappingAccessor) keyMapping).akythera$dispatchInputActions();
        }
    }

    /// Notifies every listener on `action` that `triggeringState` fired.
    @ApiStatus.Internal
    public void runEvent(final Holder<InputAction> action, final TriggeringState triggeringState) {
        List<InputListener> bound = listeners.get(action);

        if (bound == null) return;

        for (InputListener listener : new ArrayList<>(bound)) {
            listener.onTriggered(action.value(), triggeringState);
        }
    }

    /// Runs the callback of each [TriggeringState] one [#bindAction] binding receives.
    private static final class CallbackListener implements InputListener {
        private final @Nullable ScriptCallback started;
        private final @Nullable ScriptCallback triggered;
        private final @Nullable ScriptCallback completed;
        private final @Nullable ScriptCallback canceled;

        /// Whether the current hold already ran [#started].
        private boolean holding;

        private CallbackListener(
            final @Nullable ScriptCallback started,
            final @Nullable ScriptCallback triggered,
            final @Nullable ScriptCallback completed,
            final @Nullable ScriptCallback canceled
        ) {
            this.started = started;
            this.triggered = triggered;
            this.completed = completed;
            this.canceled = canceled;
        }

        @Override
        public void onTriggered(final InputAction inputAction, final TriggeringState triggeringState) {
            EntityPatch<?> self = AkytheraPatches.getEntityPatch(Minecraft.getInstance().getCameraEntity());

            switch (triggeringState) {
                case TRIGGERED -> {
                    if (!holding && started != null) started.runAs(self);

                    // A type that never completes has no release to end the hold.
                    holding = inputAction.triggeringType().dispatchesCompleted();

                    if (triggered != null) triggered.runAs(self);
                }
                case COMPLETED -> {
                    holding = false;

                    if (completed != null) completed.runAs(self);
                }
                case CANCELED -> {
                    holding = false;

                    if (canceled != null) canceled.runAs(self);
                }
            }
        }
    }
}
