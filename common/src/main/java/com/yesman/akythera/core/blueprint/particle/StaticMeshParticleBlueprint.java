package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraStaticMeshParticle;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/// A 3D model drawn at the particle's position, turned by its rotation and stretched by its scale.
///
/// @param texture   the texture the model is drawn with, or empty for the one the mesh's own metadata names
/// @param transform the model's offset from the emitter, its starting rotation and its starting scale, in the
///                  emitter's space
public record StaticMeshParticleBlueprint(
    ParticleProperties properties,
    AssetAccessor<? extends Mesh<?>> mesh,
    Optional<Identifier> texture,
    RGBA color,
    Transform transform
) implements ParticleBlueprint {
    public static final MapCodec<StaticMeshParticleBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleProperties.MAP_CODEC.forGetter(StaticMeshParticleBlueprint::properties),
                MeshManager.CODEC.fieldOf("mesh").forGetter(StaticMeshParticleBlueprint::mesh),
                Identifier.CODEC.optionalFieldOf("texture").forGetter(StaticMeshParticleBlueprint::texture),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(StaticMeshParticleBlueprint::color),
                Transform.CODEC.optionalFieldOf("transform", Transform.IDENTITY).forGetter(StaticMeshParticleBlueprint::transform)
            ).apply(instance, StaticMeshParticleBlueprint::new)
    );

    @Override
    public AkytheraParticle instantiate(final AkytheraParticleEmitter emitter) {
        return new AkytheraStaticMeshParticle(emitter, properties, mesh, texture.orElse(null), color, emitter.getWorldTransform().mul(transform));
    }

    @Override
    public MapCodec<StaticMeshParticleBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
