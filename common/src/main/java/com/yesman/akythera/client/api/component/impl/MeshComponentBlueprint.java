package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import org.jspecify.annotations.Nullable;

/// Builds one [MeshComponent] per host. Attachable under anything that hosts components; the host is
/// read only to find the [EntityPatch] the mesh getter runs against.
public record MeshComponentBlueprint(@Nullable PureNode meshGetter)
    implements ComponentBlueprint<MeshComponent, ComponentAttachable>
{
    public static final MapCodec<MeshComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                PureNode.CODEC.optionalFieldOf("mesh_getter")
                    .forGetter(ParseUtils.provideOptional(MeshComponentBlueprint::meshGetter))
            )
            .apply(instance, meshGetter -> new MeshComponentBlueprint(meshGetter.orElse(null)))
    );

    @Override
    public MeshComponent instantiate(final ComponentAttachable host) {
        return new MeshComponent(meshGetter, ownerOf(host));
    }

    /// The patch `host` belongs to: the patch itself, or the owner of the armature a socket rides.
    private static @Nullable EntityPatch<?> ownerOf(final ComponentAttachable host) {
        if (host instanceof EntityPatch<?> patch) return patch;

        if (host instanceof BoneSocket socket) {
            ArmatureComponent armature = socket.getArmature();
            return armature == null ? null : armature.getOwner();
        }

        return null;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<MeshComponent, ComponentAttachable>> mapCodec() {
        return CODEC;
    }
}
