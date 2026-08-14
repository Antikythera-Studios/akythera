package com.yesman.akythera.core.math.physics.collider;

import com.yesman.akythera.core.math.physics.collider.component.ColliderComponent;
import com.yesman.akythera.core.math.physics.collider.component.LineColliderComponent;
import com.yesman.akythera.core.math.physics.collider.component.OBBColliderComponent;

import java.util.HashMap;
import java.util.Map;

/// The collision logic table: one entry per unordered [ColliderType] pair.
///
/// Shapes don't know how to hit each other — the pair does. An addon can therefore teach a built-in
/// collider to hit a shape of its own, or replace a built-in pair, without touching either class.
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

    /// Whether the two colliders overlap. An unregistered shape pair never collides rather than
    /// throwing — an addon's collider must not be able to kill a hit sweep.
    ///
    /// Both colliders are taken as already placed; the caller applies their transforms
    /// ([ColliderComponent#applyTransform]) before asking.
    public static boolean overlap(final ColliderComponent<?> collider1, final ColliderComponent<?> collider2) {
        Entry entry = LOGICS.get(TypePair.of(collider1.type(), collider2.type()));

        if (entry == null) return false;
        // Cheap reject: the exact test only runs for pairs whose outer boxes already touch.
        if (!collider1.getOuterAABB().intersects(collider2.getOuterAABB())) return false;

        return entry.first() == collider1.type()
            ? entry.logic().checkCollision(collider1, collider2)
            : entry.logic().checkCollision(collider2, collider1);
    }

    /// Built-in shape pairs. Called once the collider types are loaded.
    public static void registerBuiltInLogics() {
        registerCollisionLogic(
            AkytheraColliderType.ORIENTATED_BOUNDING_BOX, AkytheraColliderType.ORIENTATED_BOUNDING_BOX,
            OBBColliderComponent::checkCollisionWithOBB
        );
        registerCollisionLogic(
            AkytheraColliderType.LINE, AkytheraColliderType.ORIENTATED_BOUNDING_BOX,
            LineColliderComponent::checkCollisionWithOBB
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
    /// (line against box) is always called with its arguments the way it was written.
    private record Entry(ColliderType first, CollisionCheck<ColliderComponent<?>, ColliderComponent<?>> logic) {}
}
