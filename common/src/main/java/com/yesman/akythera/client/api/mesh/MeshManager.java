package com.yesman.akythera.client.api.mesh;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.DynamicAssetManager;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.core.asset.loader.MeshLoader;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.client.core.asset.mesh.StaticMesh;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public final class MeshManager extends DynamicAssetManager<@NotNull Mesh<?>> {
    public static final String DIRECTORY = "animmodels/meshes/";
    public static final MeshManager INSTANCE = new MeshManager();

    public static MeshManager getInstance() {
        return INSTANCE;
    }

    /// Serialized form is the trimmed id (no [#DIRECTORY] prefix), which is also how
    /// [#issuedAccessor] is keyed here. Unlike [com.yesman.akythera.api.asset.armature.ArmatureManager#CODEC],
    /// decode cannot issue on demand — the id alone doesn't say whether the mesh is static or
    /// skeletal, and each accessor needs its type-specific constructor — so an id nothing has
    /// issued yet decodes to a proper error instead of poisoning the record codec with a null.
    public static final Codec<AssetAccessor<? extends Mesh<?>>> CODEC =
        Identifier.CODEC.comapFlatMap(
            id -> {
                AssetAccessor<? extends Mesh<?>> accessor = INSTANCE.getRegisteredAccessorById(id);
                return accessor == null
                    ? DataResult.error(() -> "No mesh accessor has been issued for " + id)
                    : DataResult.success(accessor);
            },
            accessor ->
                Identifier.fromNamespaceAndPath(
                    accessor.identifier().getNamespace(),
                    accessor.identifier().getPath().replace(DIRECTORY, "") // trim asset directory
                )
        );

    /// Returns a [StaticMesh] accessor by id
    ///
    /// Assets are loaded in first reference, by [AssetAccessor#get].
    @SuppressWarnings("unchecked")
    public static <T extends StaticMesh> AssetAccessor<T> staticMesh(
        String namespace,
        String path,
        MeshLoader.MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor
    ) throws IllegalArgumentException {
        return (AssetAccessor<T>)INSTANCE.issuedAccessor.computeIfAbsent(
            Identifier.fromNamespaceAndPath(namespace, path),
            _ -> new StaticMeshAccessor<>(Identifier.fromNamespaceAndPath(namespace, DIRECTORY + path), meshConstructor)
        );
    }

    /// Returns a [SkeletalMesh] accessor by id
    ///
    /// Assets are loaded in first reference, by [AssetAccessor#get].
    @SuppressWarnings("unchecked")
    public static <T extends SkeletalMesh> AssetAccessor<T> skeletalMesh(
        String namespace,
        String path,
        MeshLoader.MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor
    ) throws IllegalArgumentException {
        return (AssetAccessor<T>)INSTANCE.issuedAccessor.computeIfAbsent(
            Identifier.fromNamespaceAndPath(namespace, path),
            _ -> new SkeletalMeshAccessor<>(Identifier.fromNamespaceAndPath(namespace, DIRECTORY + path), meshConstructor)
        );
    }

    @Override
    public void onAssetReloaded() {
        for (Mesh<?> mesh : loadedObjectsRegistry.values()) {
            // Explicit call to release buffers
            if (mesh != null) mesh.destroy();
        }

        super.onAssetReloaded();
    }

    /// Accessor for [StaticMesh]
    private record StaticMeshAccessor<T extends StaticMesh>(
        Identifier identifier,
        MeshLoader.MeshConstructor<T, Mesh.CommonMeshBuilder<StaticMesh.StaticMeshPart>> meshConstructor
    ) implements AssetAccessor<T> {
        @Override @SuppressWarnings("unchecked")
        public T get() {
            try {
                return (T)INSTANCE.loadedObjectsRegistry.computeIfAbsent(identifier, id ->
                    MeshLoader.getLoader(id, ComponentsFactory.getImpl().getResourceManager())
                        .loadStaticMesh(meshConstructor)
                );
            } catch (ResourceLoadingFailureException e) {
                // leave null object for given identifier. live until the game is reloaded
                INSTANCE.loadedObjectsRegistry.put(identifier, null);
                throw e;
            }
        }

        @Override
        public Type type() {
            return Type.STATIC_MESH;
        }

        @Override
        public @NotNull String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof StaticMeshAccessor(Identifier identifier1, MeshLoader.MeshConstructor<?, ?> _)) {
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

    /// Accessor for [SkeletalMesh]
    private record SkeletalMeshAccessor<T extends SkeletalMesh>(
        Identifier identifier,
        MeshLoader.MeshConstructor<T, SkeletalMesh.SkeletalMeshBuilder> meshConstructor
    ) implements AssetAccessor<T> {
        @Override @SuppressWarnings("unchecked")
        public T get() {
            try {
                return (T)INSTANCE.loadedObjectsRegistry.computeIfAbsent(
                    identifier,
                    id -> MeshLoader.getLoader(id, ComponentsFactory.getImpl().getResourceManager())
                        .loadSkeletalMesh(meshConstructor)
                );
            } catch (ResourceLoadingFailureException e) {
                // leave null object for given identifier. live until the game is reloaded
                INSTANCE.loadedObjectsRegistry.put(identifier, null);
                throw e;
            }
        }

        @Override
        public Type type() {
            return Type.SKELETAL_MESH;
        }

        @Override
        public @NotNull String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof StaticMeshAccessor(Identifier identifier1, MeshLoader.MeshConstructor<?, ?> _)) {
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
}