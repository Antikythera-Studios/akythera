package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

@NullMarked
public record ClientBoundDataBlockSync(Identifier dataBlockType, Map<Identifier, Tag> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundDataBlockSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "data_block_sync_client"));

    public static final StreamCodec<ByteBuf, ClientBoundDataBlockSync> STREAM_CODEC =
        StreamCodec.composite(
            Identifier.STREAM_CODEC,
            ClientBoundDataBlockSync::dataBlockType,
            ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.TAG),
            ClientBoundDataBlockSync::entries,
            ClientBoundDataBlockSync::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
