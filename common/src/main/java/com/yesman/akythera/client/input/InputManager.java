package com.yesman.akythera.client.input;

import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.input.TriggeringState;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.mixinaccessor.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.ApiStatus;

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

    private InputManager() {}

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

    /// Key mappings the per-tick driver scans, copied because a listener may bind or unbind one
    /// while the driver is still iterating.
    @ApiStatus.Internal
    public Set<KeyMapping> boundKeyMappings() {
        return Set.copyOf(boundKeyMappings);
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
}
