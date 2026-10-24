package com.yesman.akythera.api.asset.physics;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.DynamicAssetManager;
import com.yesman.akythera.api.asset.armature.ArmatureManager;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.AssetLoader;
import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ArmaturePhysicsManager extends DynamicAssetManager<@NonNull ArmaturePhysicsData> {
    public static final String DIRECTORY = "animmodels/armature_physics/";

    public static final ArmaturePhysicsManager INSTANCE = new ArmaturePhysicsManager();

    public static ArmaturePhysicsManager getInstance() {
        return INSTANCE;
    }

    /// Serialized form is the trimmed id, without [#DIRECTORY].
    public static final Codec<AssetAccessor<? extends ArmaturePhysicsData>> CODEC =
        Identifier.CODEC.xmap(
            ArmaturePhysicsManager::accessor,
            accessor ->
                Identifier.fromNamespaceAndPath(
                    accessor.identifier().getNamespace(),
                    accessor.identifier().getPath().replace(DIRECTORY, "")
                )
        );

    /// Returns an accessor by trimmed id. The asset loads on the first [AssetAccessor#get].
    public static AssetAccessor<ArmaturePhysicsData> accessor(final Identifier id) {
        Identifier fullId = Identifier.fromNamespaceAndPath(id.getNamespace(), DIRECTORY + id.getPath());

        @SuppressWarnings("unchecked")
        AssetAccessor<ArmaturePhysicsData> accessor = (AssetAccessor<ArmaturePhysicsData>) INSTANCE.issuedAccessor
            .computeIfAbsent(fullId, ArmaturePhysicsAccessor::new);

        return accessor;
    }

    /// The physics asset at `armature`'s own path, or null when the armature has none.
    public static @Nullable ArmaturePhysicsData ofArmature(final AssetAccessor<? extends Armature> armature) {
        Identifier armatureId = armature.identifier();

        if (!armatureId.getPath().startsWith(ArmatureManager.DIRECTORY)) return null;

        AssetAccessor<ArmaturePhysicsData> physics = accessor(Identifier.fromNamespaceAndPath(
            armatureId.getNamespace(),
            armatureId.getPath().substring(ArmatureManager.DIRECTORY.length())
        ));

        if (!INSTANCE.loadedObjectsRegistry.containsKey(physics.identifier())) {
            Identifier asJson = physics.identifier().withSuffix(".json");

            if (ComponentsFactory.getImpl().getResourceManager().getResource(asJson).isEmpty()) {
                INSTANCE.loadedObjectsRegistry.put(physics.identifier(), null);
            }
        }

        return physics.getOrHandleException(e -> Akythera.LOGGER.error("Failed at loading armature physics {}", physics.identifier(), e));
    }

    private record ArmaturePhysicsAccessor(Identifier identifier) implements AssetAccessor<ArmaturePhysicsData> {
        @Override
        public @Nullable ArmaturePhysicsData get() {
            if (INSTANCE.loadedObjectsRegistry.containsKey(identifier)) {
                return INSTANCE.loadedObjectsRegistry.get(identifier);
            }

            try {
                return INSTANCE.loadedObjectsRegistry.computeIfAbsent(identifier, id ->
                    AssetLoader.readJsonAsset(id, ComponentsFactory.getImpl().getResourceManager(), ArmaturePhysicsData.CODEC)
                );
            } catch (ResourceLoadingFailureException e) {
                // leave null object for given identifier. live until the game is reloaded
                INSTANCE.loadedObjectsRegistry.put(identifier, null);
                throw e;
            }
        }

        @Override
        public Type type() {
            return Type.ARMATURE_PHYSICS;
        }

        @Override
        public @NonNull String toString() {
            return identifier.toString();
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof ArmaturePhysicsAccessor(Identifier other)) {
                return identifier.equals(other);
            } else if (obj instanceof Identifier other) {
                return identifier.equals(other);
            } else if (obj instanceof String name) {
                return identifier.toString().equals(name);
            } else {
                return false;
            }
        }
    }
}
