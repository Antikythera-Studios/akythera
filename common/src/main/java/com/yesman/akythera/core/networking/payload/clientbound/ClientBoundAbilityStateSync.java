package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// One slot's [AbilityState], as its client bound codec wrote it on the server thread.
///
/// A payload is decoded before its handler holds the entity the state belongs to, and which codec
/// reads it depends on the ability in the slot, so this carries bytes.
@NullMarked
public record ClientBoundAbilityStateSync(
    int entityId,
    AbilitySlot slot,
    byte[] state
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundAbilityStateSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityStateSync> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityStateSync::entityId,
            AbilitySlot.ENUM_MANAGER.getStreamCodec(),
            ClientBoundAbilityStateSync::slot,
            ByteBufCodecs.BYTE_ARRAY,
            ClientBoundAbilityStateSync::state,
            ClientBoundAbilityStateSync::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
