package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.BlockSlam;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/// Plays a [BlockSlam] at `center`, already placed on the ground block, over `radius` blocks.
@NullMarked
public record ClientBoundBlockSlam(Vec3 center, double radius) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundBlockSlam> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "block_slam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundBlockSlam> STREAM_CODEC =
        StreamCodec.composite(
            Vec3.STREAM_CODEC,
            ClientBoundBlockSlam::center,
            ByteBufCodecs.DOUBLE,
            ClientBoundBlockSlam::radius,
            ClientBoundBlockSlam::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
