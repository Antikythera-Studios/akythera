package com.yesman.akythera.api.input;

/// How a key's raw state is read. One [TriggeringType] dispatches one or more
/// [TriggeringState]s, and an input action names exactly one of these.
///
/// | Type       | Triggered                   | Completed                        | Canceled                        |
/// |------------|-----------------------------|----------------------------------|---------------------------------|
/// | Press      | press edge                  | never                            | never                           |
/// | Down       | every tick the key is down  | never                            | never                           |
/// | Release    | release edge                | never                            | never                           |
/// | Hold       | every tick the key is down  | release                          | suppressed mid-hold             |
/// | Hold Until | every tick the key is down  | release at or above the threshold | release below the threshold, or suppressed mid-hold |
///
/// The held types dispatch nothing until their [InputAction#holdStartTicks] pass, and count the threshold from
/// then. A release after a held action started dispatches no Release.
public enum TriggeringType {
    PRESS,
    DOWN,
    RELEASE,
    HOLD,
    HOLD_UNTIL;

    /// Whether this type ever dispatches [TriggeringState#COMPLETED].
    public boolean dispatchesCompleted() {
        return this == HOLD || this == HOLD_UNTIL;
    }

    /// Whether this type ever dispatches [TriggeringState#CANCELED].
    public boolean dispatchesCanceled() {
        return this == HOLD || this == HOLD_UNTIL;
    }

    /// Whether this type reads a held duration against a threshold.
    public boolean isHeld() {
        return this == HOLD || this == HOLD_UNTIL;
    }
}
