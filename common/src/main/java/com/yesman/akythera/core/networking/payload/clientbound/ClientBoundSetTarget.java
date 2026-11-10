package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.OptionalInt;

/// Sets the target of the entity on a client, see [EntityPatch#setTarget]. An empty `targetId` clears it.
@NullMarked
public record ClientBoundSetTarget(int entityId, OptionalInt targetId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundSetTarget> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "set_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundSetTarget> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientBoundSetTarget::entityId,
            ByteBufCodecs.OPTIONAL_VAR_INT,
            ClientBoundSetTarget::targetId,
            ClientBoundSetTarget::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
