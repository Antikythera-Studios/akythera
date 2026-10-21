package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

@NullMarked
public record ClientBoundStopAnimMontage(Optional<ResourceKey<AnimationMontage>> montage, int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundStopAnimMontage> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "stop_anim_montage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundStopAnimMontage> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.optional(ResourceKey.streamCodec(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY)),
            ClientBoundStopAnimMontage::montage,
            ByteBufCodecs.INT,
            ClientBoundStopAnimMontage::entityId,
            ClientBoundStopAnimMontage::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
