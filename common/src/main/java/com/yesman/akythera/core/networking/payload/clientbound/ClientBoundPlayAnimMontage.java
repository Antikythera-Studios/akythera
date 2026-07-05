package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ClientBoundPlayAnimMontage(Identifier montageId, String montageSection, int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundPlayAnimMontage> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "play_anim_montage"));

    public static final StreamCodec<ByteBuf, ClientBoundPlayAnimMontage> STREAM_CODEC =
        StreamCodec.composite(
            Identifier.STREAM_CODEC,
            ClientBoundPlayAnimMontage::montageId,
            ByteBufCodecs.STRING_UTF8,
            ClientBoundPlayAnimMontage::montageSection,
            ByteBufCodecs.INT,
            ClientBoundPlayAnimMontage::entityId,
            ClientBoundPlayAnimMontage::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
