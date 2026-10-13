package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraSkeletalMeshParticle;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.blueprint.ArmatureComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/// A skinned 3D model drawn at the particle's position, posed by its own armature or by the pose of the entity its
/// system is attached to. With neither, the model is drawn as authored.
///
/// @param armature        the particle's own armature component, whose animator script poses the model, or empty
/// @param copyAttachedPose whether the particle takes the pose the entity its system is attached to holds when it
///                         spawns, and keeps it, in place of `armature`'s. Falls back to `armature`'s when the system
///                         is attached to no entity with an armature
/// @param texture         the texture the model is drawn with, or empty for the one the mesh's own metadata names
/// @param transform       the model's offset from the emitter, its starting rotation and its starting scale, in the
///                        emitter's space
public record SkeletalMeshParticleBlueprint(
    ParticleProperties properties,
    AssetAccessor<? extends Mesh<?>> mesh,
    Optional<ArmatureComponentBlueprint> armature,
    boolean copyAttachedPose,
    Optional<Identifier> texture,
    RGBA color,
    Transform transform
) implements ParticleBlueprint {
    public static final MapCodec<SkeletalMeshParticleBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ParticleProperties.MAP_CODEC.forGetter(SkeletalMeshParticleBlueprint::properties),
                MeshManager.CODEC.fieldOf("mesh").forGetter(SkeletalMeshParticleBlueprint::mesh),
                ArmatureComponentBlueprint.CODEC.codec().optionalFieldOf("armature").forGetter(SkeletalMeshParticleBlueprint::armature),
                Codec.BOOL.optionalFieldOf("copy_attached_pose", false).forGetter(SkeletalMeshParticleBlueprint::copyAttachedPose),
                Identifier.CODEC.optionalFieldOf("texture").forGetter(SkeletalMeshParticleBlueprint::texture),
                RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(SkeletalMeshParticleBlueprint::color),
                Transform.CODEC.optionalFieldOf("transform", Transform.IDENTITY).forGetter(SkeletalMeshParticleBlueprint::transform)
            ).apply(instance, SkeletalMeshParticleBlueprint::new)
    );

    /// @throws IllegalStateException when `mesh` is no skinned mesh
    @Override
    public AkytheraParticle instantiate(final AkytheraParticleEmitter emitter) {
        return new AkytheraSkeletalMeshParticle(emitter, this);
    }

    @Override
    public MapCodec<SkeletalMeshParticleBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
