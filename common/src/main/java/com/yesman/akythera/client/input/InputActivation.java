package com.yesman.akythera.client.input;

/// A triggering timing of bound input action. Each [InputMode]
/// trigger input events based on their context, tagging an event
/// with [InputActivation].
public enum InputActivation {
    /// First time the key is pressed
    PRESS,

    /// First time the key is pressed. If threshold time exists,
    /// called after it has elapsed.
    START,

    /// Called in each tick the key is pressed before threshold time has elapsed.
    DOWN,

    /// Called in each tick the key is pressed after threshold time has elapsed.
    HOLD,

    /// Called when the key is released no matter threshold time is set.
    RELEASE,

    /// Called when the key is released before threshold time has elapsed.
    CANCEL,

    /// Called when the key is released after threshold time has elapsed.
    COMPLETE,
}
