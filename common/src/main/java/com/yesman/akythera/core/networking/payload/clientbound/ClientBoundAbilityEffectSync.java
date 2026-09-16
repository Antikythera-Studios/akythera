package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilityEffectInstance;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.core.Akythera;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// Where one live [AbilityEffectInstance] stands on the server, named by its effect id and the
/// modifier index the server numbered it with. `ended` drops the client's copy; anything else
/// creates or overwrites it.
///
/// The modifier list, the resource deltas and the applied flag stay on the server.
@NullMarked
public record ClientBoundAbilityEffectSync(
    int entityId,
    Identifier effectId,
    int modifierIndex,
    AbilityEffectInstance.Role role,
    Optional<AbilitySlot> source,
    int remainingTicks,
    int stacks,
    boolean ended
) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, AbilityEffectInstance.Role> ROLE_STREAM_CODEC =
        ByteBufCodecs.idMapper(
            ordinal -> AbilityEffectInstance.Role.values()[ordinal],
            AbilityEffectInstance.Role::ordinal
        );

    public static final CustomPacketPayload.Type<ClientBoundAbilityEffectSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_effect_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityEffectSync> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityEffectSync::entityId,
            Identifier.STREAM_CODEC,
            ClientBoundAbilityEffectSync::effectId,
            ByteBufCodecs.VAR_INT,
            ClientBoundAbilityEffectSync::modifierIndex,
            ROLE_STREAM_CODEC,
            ClientBoundAbilityEffectSync::role,
            ByteBufCodecs.optional(AbilitySlot.ENUM_MANAGER.getStreamCodec()),
            ClientBoundAbilityEffectSync::source,
            ByteBufCodecs.VAR_INT,
            ClientBoundAbilityEffectSync::remainingTicks,
            ByteBufCodecs.VAR_INT,
            ClientBoundAbilityEffectSync::stacks,
            ByteBufCodecs.BOOL,
            ClientBoundAbilityEffectSync::ended,
            ClientBoundAbilityEffectSync::new
        );

    public static ClientBoundAbilityEffectSync of(
        final int entityId,
        final AbilityEffectInstance instance,
        final boolean ended
    ) {
        AbilityInstance source = instance.getSource();

        return new ClientBoundAbilityEffectSync(
            entityId,
            instance.getEffect().getId(),
            instance.getModifierIndex(),
            instance.getRole(),
            Optional.ofNullable(source == null ? null : source.getSlot()),
            instance.getRemainingTicks(),
            instance.getStacks(),
            ended
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
