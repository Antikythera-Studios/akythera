package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// Starts the rag doll of the entity's first armature on a client, see [ArmatureComponent#startRagdoll].
@NullMarked
public record ClientBoundStartRagdoll(int entityId, float velocityScale, int durationTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundStartRagdoll> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "start_ragdoll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundStartRagdoll> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientBoundStartRagdoll::entityId,
            ByteBufCodecs.FLOAT,
            ClientBoundStartRagdoll::velocityScale,
            ByteBufCodecs.VAR_INT,
            ClientBoundStartRagdoll::durationTicks,
            ClientBoundStartRagdoll::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
