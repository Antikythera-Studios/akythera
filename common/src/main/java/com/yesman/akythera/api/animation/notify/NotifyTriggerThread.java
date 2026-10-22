package com.yesman.akythera.api.animation.notify;

public enum NotifyTriggerThread {
    /// A notify that is checked in each frame time. Accurate, but expensive.
    RENDER,

    /// A notify that is checked in each logical tick. Optimized, but has a margin of error.
    ///
    /// Server side notifies are all fixed to this option.
    WORLD
}
