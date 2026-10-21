package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.mesh.MeshManager;
import com.yesman.akythera.client.core.asset.mesh.Mesh;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

/// Builds one [MeshComponent] per host. Attachable under anything that hosts components; the host is
/// read for the [EntityPatch] the render type getter runs against, the [ArmatureComponent] that poses
/// the mesh, and the space it is drawn in.
///
/// @param transform              where the mesh sits in its host's space, or in its armature's for a skeletal mesh
/// @param transformInheritPolicy the elements of that space the mesh takes on
public record MeshComponentBlueprint(
    @Nullable AssetAccessor<? extends Mesh<?>> mesh,
    @Nullable AssetAccessor<? extends Mesh<?>> slimMesh,
    @Nullable PureNode renderTypeGetter,
    Transform transform,
    TransformInheritPolicy transformInheritPolicy,
    boolean enabled
)
    implements ComponentBlueprint<MeshComponent, ComponentAttachable>
{
    public static final MapCodec<MeshComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                MeshManager.CODEC.optionalFieldOf("mesh")
                    .forGetter(ParseUtils.provideOptional(MeshComponentBlueprint::mesh)),
                MeshManager.CODEC.optionalFieldOf("slim_mesh")
                    .forGetter(ParseUtils.provideOptional(MeshComponentBlueprint::slimMesh)),
                PureNode.CODEC.optionalFieldOf("render_type_getter")
                    .forGetter(ParseUtils.provideOptional(MeshComponentBlueprint::renderTypeGetter)),
                TRANSFORM_CODEC.forGetter(MeshComponentBlueprint::transform),
                TRANSFORM_INHERIT_POLICY_CODEC.forGetter(MeshComponentBlueprint::transformInheritPolicy),
                ENABLED_CODEC.forGetter(MeshComponentBlueprint::enabled)
            )
            .apply(instance, (mesh, slimMesh, renderTypeGetter, transform, transformInheritPolicy, enabled) ->
                new MeshComponentBlueprint(mesh.orElse(null), slimMesh.orElse(null), renderTypeGetter.orElse(null), transform, transformInheritPolicy, enabled))
    );

    @Override
    public MeshComponent instantiate(final ComponentAttachable host) {
        MeshComponent component = new MeshComponent(
            mesh,
            slimMesh,
            renderTypeGetter,
            ownerOf(host),
            armatureOf(host),
            host,
            transform,
            transformInheritPolicy
        );

        component.setEnabled(enabled);

        return component;
    }

    /// The patch `host` belongs to: the patch itself, the armature's owner, or the owner of the
    /// armature a socket rides. Null when that owner is not a patch.
    private static @Nullable EntityPatch<?> ownerOf(final ComponentAttachable host) {
        return switch (host) {
            case EntityPatch<?> patch -> patch;
            case ArmatureComponent armature -> armature.getOwner() instanceof EntityPatch<?> patch ? patch : null;
            case TransformSocket socket -> socket.getArmature() != null && socket.getArmature().getOwner() instanceof EntityPatch<?> patch ? patch : null;
            default -> null;
        };
    }

    /// The armature that poses this mesh: the host itself, the one a socket rides, or a patch's first.
    static @Nullable ArmatureComponent armatureOf(final ComponentAttachable host) {
        return switch (host) {
            case ArmatureComponent armature -> armature;
            case TransformSocket socket -> socket.getArmature();
            case EntityPatch<?> patch -> patch.getArmatureComponent();
            default -> null;
        };
    }

    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<MeshComponent, ComponentAttachable>> mapCodec() {
        return CODEC;
    }
}
