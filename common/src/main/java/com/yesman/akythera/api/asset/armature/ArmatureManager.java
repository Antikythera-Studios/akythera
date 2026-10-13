package com.yesman.akythera.api.asset.armature;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.DynamicAssetManager;
import com.yesman.akythera.api.asset.SingletonAccessor;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class ArmatureManager extends DynamicAssetManager<@NonNull Armature> {
    public static final String DIRECTORY = "animmodels/armatures/";

    /// The armature a slot left unfilled resolves to: [ArmatureLoader#ROOT_BONE] and nothing else.
    public static final AssetAccessor<Armature> EMPTY_ARMATURE_ACCESSOR = new SingletonAccessor<>(
        Identifier.fromNamespaceAndPath(Akythera.MODID, "empty"),
        emptyArmature(),
        AssetAccessor.Type.ARMATURE
    );

    public static final ArmatureManager INSTANCE = new ArmatureManager();

    public static ArmatureManager getInstance() {
        return INSTANCE;
    }

    /// Serialized form is the trimmed id (no [#DIRECTORY] prefix), matching what encode writes.
    /// Decode issues the accessor on demand rather than reading [#issuedAccessor] directly: the
    /// map is keyed by the full prefixed id and only holds accessors something already requested,
    /// so a plain `get` on the trimmed id always misses and poisons the record codec with a null.
    public static final Codec<AssetAccessor<? extends Armature>> CODEC =
        Identifier.CODEC.xmap(
            id -> accessor(id.getNamespace(), id.getPath(), Armature::new),
            accessor ->
                Identifier.fromNamespaceAndPath(
                    accessor.identifier().getNamespace(),
                    accessor.identifier().getPath().replace(DIRECTORY, "") // trim asset directory
                )
        );

    /// Returns an accessor with default armature factory
    @SuppressWarnings("unchecked")
    public static <T extends Armature> AssetAccessor<T> accessor(String namespace, String path, ArmatureLoader.ArmatureConstructor<T> armatureConstructor) {
        // Hardcode empty armature accessor path
        if (EMPTY_ARMATURE_ACCESSOR.identifier().equals(Identifier.fromNamespaceAndPath(namespace, path))) {
            return (AssetAccessor<T>)EMPTY_ARMATURE_ACCESSOR;
        }

        return (AssetAccessor<T>)INSTANCE.issuedAccessor.computeIfAbsent(
            Identifier.fromNamespaceAndPath(namespace, DIRECTORY + path),
            _ ->
                new ArmatureAccessor<>(
                    Identifier.fromNamespaceAndPath(namespace, DIRECTORY + path),
                    armatureConstructor
                )
        );
    }

    /// Accessor for armature data stored in [AnimationClip]
    private record ArmatureAccessor<T extends Armature>(
        Identifier identifier,
        ArmatureLoader.ArmatureConstructor<T> armatureConstructor
    ) implements AssetAccessor<T> {
        @Override @SuppressWarnings("unchecked")
        public T get() {
            try {
                return (T)INSTANCE.loadedObjectsRegistry.computeIfAbsent(identifier, id ->
                    ArmatureLoader.getLoader(id, ComponentsFactory.getImpl().getResourceManager()).loadArmature(armatureConstructor)
                );
            } catch (ResourceLoadingFailureException e) {
                // leave null object for given identifier. live until the game is reloaded
                INSTANCE.loadedObjectsRegistry.put(identifier, null);
                throw e;
            }
        }

        @Override
        public Type type() {
            return Type.ARMATURE;
        }

        @Override
        public @NonNull String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof ArmatureAccessor(Identifier identifier1, ArmatureLoader.ArmatureConstructor<?> _)) {
                return identifier.equals(identifier1);
            } else if (obj instanceof Identifier rl) {
                return identifier.equals(rl);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }

    /// A one-bone armature, standing [ArmatureLoader#GENERATED_ROOT_TAIL_HEIGHT] tall at the origin.
    private static Armature emptyArmature() {
        Bone root = new Bone(
            ArmatureLoader.ROOT_BONE,
            0,
            new Matrix4f(),
            new Vector3f(0.0F, ArmatureLoader.GENERATED_ROOT_TAIL_HEIGHT, 0.0F)
        );

        root.initOriginTransform(new Matrix4f());

        return new Armature(root, 1, Map.of(ArmatureLoader.ROOT_BONE, root));
    }
}