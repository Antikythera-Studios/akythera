package com.yesman.akythera.client.api.gui;

/// Widgets that have a destroy action (especially create & bind buffers via Graphic API)
public interface CloseableWidget {
    /// Unbind graphic buffers & destroy temporary resources
    void destroy();
}
