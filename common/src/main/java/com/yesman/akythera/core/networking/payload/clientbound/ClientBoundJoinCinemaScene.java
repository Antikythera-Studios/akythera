package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.cinema.CinemaSceneRoot;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// Starts one actor's part of a [CinemaScene] on a client.
///
/// @param role         the actor's role in `scene`
/// @param rootEntityId the entity the scene follows, or empty for a fixed root
/// @param rootPosition where the root is when the scene starts, in world space
/// @param rootYRot     where the root faces when the scene starts, in degrees
@NullMarked
public record ClientBoundJoinCinemaScene(
    ResourceKey<CinemaScene> scene, String role, int entityId, Optional<Integer> rootEntityId, Vec3 rootPosition, float rootYRot
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundJoinCinemaScene> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "join_cinema_scene"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundJoinCinemaScene> STREAM_CODEC =
        StreamCodec.composite(
            ResourceKey.streamCodec(AkytheraRegistries.DataPack.CINEMA_SCENE_KEY),
            ClientBoundJoinCinemaScene::scene,
            ByteBufCodecs.STRING_UTF8,
            ClientBoundJoinCinemaScene::role,
            ByteBufCodecs.INT,
            ClientBoundJoinCinemaScene::entityId,
            ByteBufCodecs.optional(ByteBufCodecs.INT),
            ClientBoundJoinCinemaScene::rootEntityId,
            Vec3.STREAM_CODEC,
            ClientBoundJoinCinemaScene::rootPosition,
            ByteBufCodecs.FLOAT,
            ClientBoundJoinCinemaScene::rootYRot,
            ClientBoundJoinCinemaScene::new
        );

    public static ClientBoundJoinCinemaScene of(final ResourceKey<CinemaScene> scene, final String role, final int entityId, final CinemaSceneRoot root) {
        return switch (root) {
            case CinemaSceneRoot.At at -> new ClientBoundJoinCinemaScene(scene, role, entityId, Optional.empty(), at.position(), at.yRot());
            case CinemaSceneRoot.On on -> {
                Entity entity = on.entity();
                float yRot = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();

                yield new ClientBoundJoinCinemaScene(scene, role, entityId, Optional.of(entity.getId()), entity.position(), yRot);
            }
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
