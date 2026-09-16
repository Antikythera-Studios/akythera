package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// Cancels every live cue play on an entity whose cue carries a tag matching `query`. The client
/// runs the same match the server ran.
@NullMarked
public record ClientBoundCancelAbilityCue(int entityId, AbilityTag query) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundCancelAbilityCue> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "cancel_ability_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundCancelAbilityCue> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundCancelAbilityCue::entityId,
            AbilityTag.ENUM_MANAGER.getStreamCodec(),
            ClientBoundCancelAbilityCue::query,
            ClientBoundCancelAbilityCue::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
