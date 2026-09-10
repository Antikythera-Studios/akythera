package com.yesman.akythera.core.math.physics.collider;

import com.yesman.akythera.core.math.physics.collider.component.CapsuleColliderComponent;
import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akythera.core.math.physics.collider.component.SphereColliderComponent;

import java.util.HashMap;
import java.util.Map;

/// The collision logic override table: one entry per unordered [ColliderType] pair.
///
/// Any pair of convex shapes is already decided by [GjkIntersection] from their support functions, so
/// a shape needs no entry here to collide with anything. An entry replaces that general answer for
/// one pair—a closed form that is cheaper or exact, or an addon's own rule—without touching either
/// shape class.
public final class CollisionDetectionLogics {
    private CollisionDetectionLogics() {}

    private static final Map<TypePair, Entry> LOGICS = new HashMap<>();

    @FunctionalInterface
    public interface CollisionCheck<T1 extends ColliderComponent<?>, T2 extends ColliderComponent<?>> {
        boolean checkCollision(final T1 collider1, final T2 collider2);
    }

    /// Binds the logic deciding whether a `type1` collider overlaps a `type2` one. Registering a pair
    /// twice replaces it, which is how an addon overrides a built-in check.
    @SuppressWarnings("unchecked")
    public static <T1 extends ColliderComponent<?>, T2 extends ColliderComponent<?>> void registerCollisionLogic(
        final ColliderType type1, final ColliderType type2, final CollisionCheck<T1, T2> detectionLogic
    ) {
        LOGICS.put(
            TypePair.of(type1, type2),
            new Entry(type1, (CollisionCheck<ColliderComponent<?>, ColliderComponent<?>>)detectionLogic)
        );
    }

    /// Whether the two colliders overlap. A pair with no registered logic is decided by
    /// [GjkIntersection].
    ///
    /// Both colliders are taken as already placed; the caller applies their transforms
    /// ([ColliderComponent#applyTransform]) before asking.
    public static boolean overlap(final ColliderComponent<?> collider1, final ColliderComponent<?> collider2) {
        // Cheap reject: the exact test only runs for pairs whose outer boxes already touch.
        if (!collider1.getOuterAABB().intersects(collider2.getOuterAABB())) return false;

        Entry entry = LOGICS.get(TypePair.of(collider1.type(), collider2.type()));

        if (entry == null) return GjkIntersection.overlap(collider1, collider2);

        return entry.first() == collider1.type()
            ? entry.logic().checkCollision(collider1, collider2)
            : entry.logic().checkCollision(collider2, collider1);
    }

    /// Built-in shape pairs. Called once the collider types are loaded.
    public static void registerBuiltInLogics() {
        registerCollisionLogic(
            AkytheraColliderType.SPHERE, AkytheraColliderType.SPHERE,
            SphereColliderComponent::checkCollisionWithSphere
        );
        registerCollisionLogic(
            AkytheraColliderType.SPHERE, AkytheraColliderType.CAPSULE,
            SphereColliderComponent::checkCollisionWithCapsule
        );
        registerCollisionLogic(
            AkytheraColliderType.CAPSULE, AkytheraColliderType.CAPSULE,
            CapsuleColliderComponent::checkCollisionWithCapsule
        );
    }

    /// A pair key that reads the same from either side, ordered by [ColliderType#universalOrdinal].
    private record TypePair(ColliderType lower, ColliderType higher) {
        static TypePair of(final ColliderType type1, final ColliderType type2) {
            return type1.universalOrdinal() <= type2.universalOrdinal()
                ? new TypePair(type1, type2)
                : new TypePair(type2, type1);
        }
    }

    /// A registered logic and the type it takes as its **first** argument, so an asymmetric check
    /// (sphere against capsule) is always called with its arguments the way it was written.
    private record Entry(ColliderType first, CollisionCheck<ColliderComponent<?>, ColliderComponent<?>> logic) {}
}
