package com.yesman.akythera.client.core.asset.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.physics.ArmaturePhysicsManager;
import com.yesman.akythera.client.core.asset.loader.binary.ClothPhysicsLoader;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// The cloth a [SkeletalMesh] carries, read by [ClothPhysicsLoader].
///
/// Every vertex is posed by the current animation first. A weight says how far the simulation may pull
/// a vertex off that animated position.
///
/// @param armaturePhysics the bodies this cloth collides with, or null for none
/// @param weights         per mesh position index: `0` exactly keeps the vertex on its animated position
///                        unsimulated, `(0, 1]` simulates it within `weight × max_distance` of it. A
///                        position past the end is `0`. Never written after construction.
/// @param offsetPairs     mesh position index pairs, flattened as `root, offset`. An offset position is
///                        never simulated: it keeps its animated distance from its root, turned as far as
///                        the cloth around the root turned. Never written after construction.
public record ClothPhysicsData(
    @Nullable AssetAccessor<? extends ArmaturePhysicsData> armaturePhysics,
    ClothSimulationParameters parameters,
    float[] weights,
    int[] offsetPairs
) {
    /// The cloth's `.akmeta` sidecar: everything but the weights.
    public record Metadata(@Nullable AssetAccessor<? extends ArmaturePhysicsData> armaturePhysics, ClothSimulationParameters parameters) {
        public static final Codec<Metadata> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    ArmaturePhysicsManager.CODEC.optionalFieldOf("armature_physics")
                        .forGetter(ParseUtils.provideOptional(Metadata::armaturePhysics)),
                    ClothSimulationParameters.CODEC.optionalFieldOf("parameters", ClothSimulationParameters.DEFAULT)
                        .forGetter(Metadata::parameters)
                ).apply(instance, Metadata::new)
        );

        /// What a cloth without a sidecar is read with.
        public static final Metadata DEFAULT = new Metadata((AssetAccessor<? extends ArmaturePhysicsData>) null, ClothSimulationParameters.DEFAULT);

        // Serialization
        public Metadata(final Optional<AssetAccessor<? extends ArmaturePhysicsData>> armaturePhysics, final ClothSimulationParameters parameters) {
            this(armaturePhysics.orElse(null), parameters);
        }
    }
}
