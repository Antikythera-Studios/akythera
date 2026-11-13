package com.yesman.akythera.client.api.camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.SpringArmComponent;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.math.Transform;

/// Declares a [CameraComponent] on an entity patch, or under a [SpringArmComponent] of one. Built on the client only.
///
/// @param transform              where the camera sits in third person, see [CameraComponent]
/// @param matchPlayerYawToCamera whether the player turns with the camera, or only while moving
/// @param collideWithTerrain     whether the camera's arm shrinks where blocks are in the way
/// @param probeRadius            how far the camera keeps off blocks, in blocks
/// @param enabled                whether the built [CameraComponent] starts switched on
public record CameraComponentBlueprint(Transform transform, boolean matchPlayerYawToCamera, boolean collideWithTerrain, double probeRadius, boolean enabled)
    implements ComponentBlueprint<CameraComponent, ComponentAttachable>
{
    public static final MapCodec<CameraComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                TRANSFORM_CODEC.forGetter(CameraComponentBlueprint::transform),
                Codec.BOOL.optionalFieldOf("match_player_yaw_to_camera", true).forGetter(CameraComponentBlueprint::matchPlayerYawToCamera),
                Codec.BOOL.optionalFieldOf("collide_with_terrain", true).forGetter(CameraComponentBlueprint::collideWithTerrain),
                Codec.DOUBLE.optionalFieldOf("probe_radius", 0.1D).forGetter(CameraComponentBlueprint::probeRadius),
                ENABLED_CODEC.forGetter(CameraComponentBlueprint::enabled)
            )
            .apply(instance, CameraComponentBlueprint::new)
    );

    @Override
    public CameraComponent instantiate(final ComponentAttachable blueprintOwner) {
        CameraComponent component = new CameraComponent(blueprintOwner, transform, matchPlayerYawToCamera, collideWithTerrain, probeRadius);

        component.setEnabled(enabled);

        return component;
    }

    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient;
    }

    @Override
    public MapCodec<CameraComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
