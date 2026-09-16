package com.yesman.akythera.client.input;

import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.input.TriggeringState;
import com.yesman.akythera.core.util.ClientOnly;

/// What one [InputAction] notifies. A listener receives every [TriggeringState] its action's
/// [InputAction#triggeringType] dispatches.
@ClientOnly
@FunctionalInterface
public interface InputListener {
    void onTriggered(final InputAction inputAction, final TriggeringState triggeringState);
}
