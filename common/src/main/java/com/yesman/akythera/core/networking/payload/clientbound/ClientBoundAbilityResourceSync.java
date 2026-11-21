package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilityResourceRef;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.StoredAbilityResource;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// A [StoredAbilityResource]'s value on an entity's [AbilitySystemComponent], with the ticks left before it refills.
@NullMarked
public record ClientBoundAbilityResourceSync(
    int entityId, AbilityResourceRef resource, float value, int regenCooldown
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundAbilityResourceSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_resource_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityResourceSync> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityResourceSync::entityId,
            AbilityResourceRef.STREAM_CODEC,
            ClientBoundAbilityResourceSync::resource,
            ByteBufCodecs.FLOAT,
            ClientBoundAbilityResourceSync::value,
            ByteBufCodecs.VAR_INT,
            ClientBoundAbilityResourceSync::regenCooldown,
            ClientBoundAbilityResourceSync::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
