package com.yesman.akythera.core.networking.payload.clientbound;

import com.mojang.datafixers.util.Either;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.ByteBufCodecsExtends;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// One cue play the server started on an entity. `cueId` names the play, `cue` is the data pack cue or the
/// Java cue's script class, `source` names the slot of the ability that played it, `withState` whether the
/// script reads that ability's state, and `elapsedTicks` how far its timer has run.
@NullMarked
public record ClientBoundPlayAbilityCue(
    int entityId,
    int cueId,
    Either<ResourceKey<AbilityCueBlueprint>, Class<? extends AbilityCueScript>> cue,
    Optional<AbilitySlot> source,
    boolean withState,
    boolean endsWithAbility,
    int elapsedTicks
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundPlayAbilityCue> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "play_ability_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundPlayAbilityCue> STREAM_CODEC =
        ByteBufCodecsExtends.composite7(
            ByteBufCodecs.INT,
            ClientBoundPlayAbilityCue::entityId,
            ByteBufCodecs.VAR_INT,
            ClientBoundPlayAbilityCue::cueId,
            ByteBufCodecs.either(
                ResourceKey.streamCodec(AkytheraRegistries.DataPack.ABILITY_CUE_KEY),
                ByteBufCodecsExtends.scriptClass(AbilityCueScript.class)
            ),
            ClientBoundPlayAbilityCue::cue,
            ByteBufCodecs.optional(AbilitySlot.ENUM_MANAGER.getStreamCodec()),
            ClientBoundPlayAbilityCue::source,
            ByteBufCodecs.BOOL,
            ClientBoundPlayAbilityCue::withState,
            ByteBufCodecs.BOOL,
            ClientBoundPlayAbilityCue::endsWithAbility,
            ByteBufCodecs.VAR_INT,
            ClientBoundPlayAbilityCue::elapsedTicks,
            ClientBoundPlayAbilityCue::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
