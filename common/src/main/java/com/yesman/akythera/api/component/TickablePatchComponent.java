package com.yesman.akythera.api.component;

/// A component that listens to the ticking event
public interface TickablePatchComponent<T> extends PatchComponent<T> {
    /// Called in fixed tick
    void tick();

    /// Called in each frame update. Only called in client side.
    /// (Server entities are only updated by [#tick])
    ///
    /// @param frameTime time dilation from previous to current frame. This method won't be called if frameTime == 0.0
    void frameUpdate(final float frameTime, final float partialTick);
}
