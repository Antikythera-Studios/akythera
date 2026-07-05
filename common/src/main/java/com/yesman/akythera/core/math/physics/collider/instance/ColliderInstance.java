package com.yesman.akythera.core.math.physics.collider.instance;

import com.yesman.akythera.core.math.Transform;
import net.minecraft.world.phys.AABB;
import com.yesman.akythera.core.math.physics.collider.Collider;

import java.util.Map;

/// A stateful instance of [Collider]
public abstract class ColliderInstance<T extends Collider> {
    /// Holding collider
    private final T collider;

    /// An [AABB] that wraps the space where the collider can exist
    protected AABB outerAABB;

    protected ColliderInstance(T collider, Transform initTransform) {
        this.collider = collider;
        this.applyTransform(initTransform);
    }

    public final T getCollider() {
        return this.collider;
    }

    /// Apply the transform to instance
    public abstract void applyTransform(Transform transform);

    /// Creates a [AABB] that wraps the space where the collider can exist
    ///
    /// Before checking collision, colliders are filtered by outer [AABB]
    /// to optimize detection algorithm
    public abstract AABB makeOuterAABB();

    /// Returns supported colliders that this collider can check collision
    public abstract Map<Class<? extends Collider>, CollisionCheck<T, ? extends Collider>> getSupportedCollideChecks();

    @SuppressWarnings("unchecked")
    public <C extends Collider> boolean isCollideWith(ColliderInstance<C> opponent) {
        if (this.getSupportedCollideChecks().containsKey(opponent.collider.getClass())) {
            CollisionCheck<T, C> collisionCheck = (CollisionCheck<T, C>)this.getSupportedCollideChecks().get(opponent.collider.getClass());
            return collisionCheck.checkCollision(this, opponent);
        }

        return false;
    }

    @FunctionalInterface
    public interface CollisionCheck<T1 extends Collider, T2 extends Collider> {
        boolean checkCollision(ColliderInstance<T1> collider1, ColliderInstance<T2> collider2);
    }
}
