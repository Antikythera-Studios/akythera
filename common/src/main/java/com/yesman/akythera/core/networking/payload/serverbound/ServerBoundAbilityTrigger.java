package com.yesman.akythera.core.networking.payload.serverbound;

import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// Asks the server to trigger the ability in `slot`, carrying whatever that ability's state wrote
/// through its server bound codec. `state` is empty when there is no such codec.
@NullMarked
public record ServerBoundAbilityTrigger(AbilitySlot slot, byte[] state) implements CustomPacketPayload {
    /// Input is a handful of fields. A longer array fails in the decoder.
    public static final int MAX_STATE_BYTES = 1024;

    public static final CustomPacketPayload.Type<ServerBoundAbilityTrigger> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_trigger"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerBoundAbilityTrigger> STREAM_CODEC =
        StreamCodec.composite(
            AbilitySlot.ENUM_MANAGER.getStreamCodec(),
            ServerBoundAbilityTrigger::slot,
            ByteBufCodecs.byteArray(MAX_STATE_BYTES),
            ServerBoundAbilityTrigger::state,
            ServerBoundAbilityTrigger::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
