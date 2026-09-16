package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// One slot's equipment on an entity's [AbilitySystemComponent].
///
/// An empty `ability` releases the slot. `activated` is false for a live give and carries the
/// current flag in a tracking snapshot.
@NullMarked
public record ClientBoundAbilityEquipmentSync(
    int entityId,
    AbilitySlot slot,
    Optional<ResourceKey<AbilityBlueprint>> ability,
    boolean activated
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundAbilityEquipmentSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_equipment_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityEquipmentSync> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityEquipmentSync::entityId,
            AbilitySlot.ENUM_MANAGER.getStreamCodec(),
            ClientBoundAbilityEquipmentSync::slot,
            ByteBufCodecs.optional(ResourceKey.streamCodec(AkytheraRegistries.DataPack.ABILITY_KEY)),
            ClientBoundAbilityEquipmentSync::ability,
            ByteBufCodecs.BOOL,
            ClientBoundAbilityEquipmentSync::activated,
            ClientBoundAbilityEquipmentSync::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
