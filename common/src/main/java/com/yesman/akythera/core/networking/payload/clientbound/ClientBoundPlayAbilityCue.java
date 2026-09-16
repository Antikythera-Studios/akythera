package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// One cue play the server started on an entity. `source` names the slot of the ability that played
/// it, and `withState` whether the behavior is handed that ability's state.
@NullMarked
public record ClientBoundPlayAbilityCue(
    int entityId,
    Identifier cueId,
    Optional<AbilitySlot> source,
    boolean withState,
    boolean endsWithAbility
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundPlayAbilityCue> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "play_ability_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundPlayAbilityCue> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundPlayAbilityCue::entityId,
            Identifier.STREAM_CODEC,
            ClientBoundPlayAbilityCue::cueId,
            ByteBufCodecs.optional(AbilitySlot.ENUM_MANAGER.getStreamCodec()),
            ClientBoundPlayAbilityCue::source,
            ByteBufCodecs.BOOL,
            ClientBoundPlayAbilityCue::withState,
            ByteBufCodecs.BOOL,
            ClientBoundPlayAbilityCue::endsWithAbility,
            ClientBoundPlayAbilityCue::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
