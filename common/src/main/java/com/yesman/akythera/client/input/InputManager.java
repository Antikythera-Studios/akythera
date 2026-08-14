package com.yesman.akythera.client.input;

import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.mixinaccessor.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/// Client-side registry of [InputAction] events, dispatched once per tick by [MixinMinecraft].
@ClientOnly
public final class InputManager {
    private static final InputManager INSTANCE = new InputManager();

    public static InputManager getInstance() {
        return INSTANCE;
    }

    /// Event to run for each registered [InputAction].
    private final Map<InputAction, Runnable> events = new HashMap<>();

    /// Key mappings with at least one binding; the per-tick driver scans only these.
    private final Set<KeyMapping> boundKeyMappings = new HashSet<>();

    private InputManager() {}

    /// Registers [event] to run when `inputAction` triggers on its [InputAction#inputActivation].
    public void registerInputAction(final InputAction inputAction, final Runnable event) {
        events.put(inputAction, event);

        final KeyMapping keyMapping = inputAction.keyMapping();
        ((KeyMappingAccessor) keyMapping).akythera$bindAction(inputAction);
        boundKeyMappings.add(keyMapping);
    }

    /// Removes `inputAction` and its event.
    public void removeInputAction(final InputAction inputAction) {
        events.remove(inputAction);

        final KeyMapping keyMapping = inputAction.keyMapping();

        if (((KeyMappingAccessor) keyMapping).akythera$unbindAction(inputAction)) {
            boundKeyMappings.remove(keyMapping);
        }
    }

    /// Key mappings the per-tick driver scans.
    @ApiStatus.Internal
    public Set<KeyMapping> boundKeyMappings() {
        return boundKeyMappings;
    }

    /// Runs the event bound to `inputAction`, if any.
    @ApiStatus.Internal
    public void runEvent(final InputAction inputAction) {
        final Runnable event = events.get(inputAction);

        if (event != null) {
            event.run();
        }
    }
}
