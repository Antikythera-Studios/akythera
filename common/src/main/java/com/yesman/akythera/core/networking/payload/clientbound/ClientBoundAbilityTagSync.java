package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// One tag grant or release on an entity's [AbilitySystemComponent].
///
/// Only the transition crosses the wire, never the grant count.
@NullMarked
public record ClientBoundAbilityTagSync(int entityId, AbilityTag tag, boolean granted) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundAbilityTagSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_tag_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityTagSync> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityTagSync::entityId,
            AbilityTag.ENUM_MANAGER.getStreamCodec(),
            ClientBoundAbilityTagSync::tag,
            ByteBufCodecs.BOOL,
            ClientBoundAbilityTagSync::granted,
            ClientBoundAbilityTagSync::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
