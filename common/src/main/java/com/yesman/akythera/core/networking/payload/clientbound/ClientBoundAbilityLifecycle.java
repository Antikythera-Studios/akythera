package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.core.Akythera;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// One activation transition of the ability in `slot`, as the server decided it.
@NullMarked
public record ClientBoundAbilityLifecycle(
    int entityId,
    AbilitySlot slot,
    Transition transition
) implements CustomPacketPayload {
    public enum Transition {
        ACTIVATED, RECAST, CANCELED, FINISHED;

        static final StreamCodec<ByteBuf, Transition> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> values()[ordinal], Transition::ordinal);
    }

    public static final CustomPacketPayload.Type<ClientBoundAbilityLifecycle> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_lifecycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityLifecycle> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityLifecycle::entityId,
            AbilitySlot.ENUM_MANAGER.getStreamCodec(),
            ClientBoundAbilityLifecycle::slot,
            Transition.STREAM_CODEC,
            ClientBoundAbilityLifecycle::transition,
            ClientBoundAbilityLifecycle::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
