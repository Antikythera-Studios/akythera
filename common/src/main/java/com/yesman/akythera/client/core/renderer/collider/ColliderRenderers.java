package com.yesman.akythera.client.core.renderer.collider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.Collider;
import com.yesman.akythera.core.math.physics.collider.ColliderType;

import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/// The renderer each [ColliderType] draws through. Registering a type twice replaces it, which is how
/// an addon restyles a built-in shape.
public final class ColliderRenderers {
    private ColliderRenderers() {}

    private static final Map<ColliderType, ColliderRenderer<?>> RENDERERS = new HashMap<>();

    public static <T extends Collider> void register(final ColliderType type, final ColliderRenderer<T> renderer) {
        RENDERERS.put(type, renderer);
    }

    /// Draws `collider` through its type's renderer. A shape with none registered draws nothing rather
    /// than throwing—an addon's collider must not be able to kill a debug pass.
    @SuppressWarnings("unchecked")
    public static void emitWireframe(
        final VertexConsumer consumer, final PoseStack.Pose pose, final Collider collider, final int color
    ) {
        ColliderRenderer<Collider> renderer = (ColliderRenderer<Collider>) RENDERERS.get(collider.type());

        if (renderer == null) return;

        renderer.emitWireframe(consumer, pose, collider, color);
    }

    /// The pose [#emitWireframe] draws `collider` under so the shape lands at `transform`, the collider's own
    /// transform. The wireframe carries [Collider#getPivotTransform] already, so the pose takes it back out.
    public static Matrix4f placement(final Collider collider, final Transform transform) {
        return transform.toMatrix().mul(collider.getPivotTransform().toMatrix().invert());
    }

    /// Built-in shape renderers. Called once the collider types are loaded.
    public static void registerBuiltInRenderers() {
        register(AkytheraColliderType.ORIENTED_BOX, new OrientedBoxColliderRenderer());
        register(AkytheraColliderType.SPHERE, new SphereColliderRenderer());
        register(AkytheraColliderType.CONE, new ConeColliderRenderer());
        register(AkytheraColliderType.CAPSULE, new CapsuleColliderRenderer());
    }
}
