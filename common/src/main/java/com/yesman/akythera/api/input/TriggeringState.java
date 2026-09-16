package com.yesman.akythera.api.input;

/// Which event a [TriggeringType] dispatched. Client side only.
public enum TriggeringState {
    TRIGGERED,

    /// The key was let go. Only [TriggeringType#HOLD] and [TriggeringType#HOLD_UNTIL] dispatch it.
    COMPLETED,

    /// The key was let go below the threshold. Only [TriggeringType#HOLD_UNTIL] dispatches it.
    CANCELED
}
