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

/// Adds a velocity to every body of the rag doll of the entity's first armature on a client, see
/// [ArmatureComponent#addRagdollVelocity].
@NullMarked
public record ClientBoundRagdollVelocity(int entityId, Vec3 velocity) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundRagdollVelocity> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ragdoll_velocity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundRagdollVelocity> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientBoundRagdollVelocity::entityId,
            Vec3.STREAM_CODEC,
            ClientBoundRagdollVelocity::velocity,
            ClientBoundRagdollVelocity::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
