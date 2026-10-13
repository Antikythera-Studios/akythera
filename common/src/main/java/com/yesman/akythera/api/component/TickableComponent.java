package com.yesman.akythera.api.component;

/// A component its host ticks while it is enabled. Headless: it needs no owner.
public interface TickableComponent extends PatchComponent {
    /// Called once per tick of its host.
    void tick();
}
