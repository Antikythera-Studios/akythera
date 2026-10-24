package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// Pushes one body of the rag doll of the entity's first armature on a client, see
/// [ArmatureComponent#applyRagdollImpulse].
@NullMarked
public record ClientBoundRagdollImpulse(int entityId, String boneName, Vec3 impulse, Optional<Vec3> worldPoint) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundRagdollImpulse> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ragdoll_impulse"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundRagdollImpulse> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientBoundRagdollImpulse::entityId,
            ByteBufCodecs.STRING_UTF8,
            ClientBoundRagdollImpulse::boneName,
            Vec3.STREAM_CODEC,
            ClientBoundRagdollImpulse::impulse,
            ByteBufCodecs.optional(Vec3.STREAM_CODEC),
            ClientBoundRagdollImpulse::worldPoint,
            ClientBoundRagdollImpulse::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
