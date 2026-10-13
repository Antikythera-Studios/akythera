package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// Plays a [ParticleSystemBlueprint] at `position`, with `parameters` decoded by the types the blueprint declares.
/// With an `attachment`, the system starts on that socket instead and follows it; `position` is where it plays when
/// the client can't find the socket.
@NullMarked
public record ClientBoundPlayParticleSystem(
    ResourceKey<ParticleSystemBlueprint> system,
    Vec3 position,
    CompoundTag parameters,
    Optional<ParticleAttachment> attachment
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundPlayParticleSystem> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "play_particle_system"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundPlayParticleSystem> STREAM_CODEC =
        StreamCodec.composite(
            ResourceKey.streamCodec(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY),
            ClientBoundPlayParticleSystem::system,
            Vec3.STREAM_CODEC,
            ClientBoundPlayParticleSystem::position,
            ByteBufCodecs.COMPOUND_TAG,
            ClientBoundPlayParticleSystem::parameters,
            ByteBufCodecs.optional(ParticleAttachment.STREAM_CODEC),
            ClientBoundPlayParticleSystem::attachment,
            ClientBoundPlayParticleSystem::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
