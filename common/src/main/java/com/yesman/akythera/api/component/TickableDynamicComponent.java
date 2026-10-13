package com.yesman.akythera.api.component;

/// A [TickableComponent] bound to its owner, also updated every frame on the client.
public interface TickableDynamicComponent<T> extends DynamicPatchComponent<T>, TickableComponent {
    /// Called in each frame update. Only called in client side.
    /// (Server entities are only updated by [#tick])
    ///
    /// @param frameTime time dilation from previous to current frame. This method won't be called if frameTime == 0.0
    void frameUpdate(final float frameTime, final float partialTick);
}
