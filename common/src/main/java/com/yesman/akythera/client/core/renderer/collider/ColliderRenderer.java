package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.core.math.physics.collider.Collider;

/// Draws one [Collider] shape as a line list, in the collider's own space.
///
/// The placement is the caller's: both destinations—the 3D viewer and the in-game hitbox pass—hand
/// over a `pose` that already carries it, so a renderer only ever reads the authored blueprint.
@FunctionalInterface
public interface ColliderRenderer<T extends Collider> {
    /// Emits this shape's wireframe. `consumer` is bound to `RenderTypes.lines()`, so every vertex
    /// carries a colour, a normal and a width—[ColliderWireframe#line] fills all three.
    void emitWireframe(final VertexConsumer consumer, final PoseStack.Pose pose, final T collider, final int color);
}
