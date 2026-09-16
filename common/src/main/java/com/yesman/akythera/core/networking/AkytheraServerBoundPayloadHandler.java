package com.yesman.akythera.core.networking;

import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.networking.payload.serverbound.ServerBoundAbilityTrigger;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

public interface AkytheraServerBoundPayloadHandler {
    /// Receiver is always a [ServerPlayer] for client bound payloads
    static ServerPlayer getServerPlayer(PayloadContext context) {
        return (ServerPlayer)context.receiver();
    }

    /// Reads the client's input into the slot's state, then turns the trigger into whatever the
    /// slot's ability makes of it. An empty slot is a miss.
    ///
    /// The target is always the sender's own player, and the read reaches only the fields the
    /// server bound codec carries.
    static void handleAbilityTrigger(final ServerBoundAbilityTrigger payload, final PayloadContext context) {
        ServerPlayer player = getServerPlayer(context);

        AkytheraPatches.getEntityPatchOptional(player).ifPresent(entityPatch -> {
            AbilitySystemComponent component = entityPatch.getComponent(AbilitySystemComponent.class);

            if (component == null) return;
            if (payload.state().length > 0 && !readClientInput(component, payload, player)) return;

            component.activateAbility(payload.slot());
        });
    }

    /// @return whether the bytes were exactly one server bound encoding of the slot's state
    private static boolean readClientInput(
        final AbilitySystemComponent component,
        final ServerBoundAbilityTrigger payload,
        final ServerPlayer player
    ) {
        AbilityInstance instance = component.getInstance(payload.slot());

        if (instance == null) return false;

        Ability<?> ability = instance.getAbility();

        // A client holds no instance of an ability it was never sent.
        if (ability == null || ability.getBlueprintKey() == null) return false;
        if (!ability.getInstantiationScope().reachesOwner()) return false;
        if (instance.getState().serverBoundCodec() == null) return false;

        return AbilityState.decode(
            instance.getState(), PacketFlow.SERVERBOUND, payload.state(), player.level()
        );
    }
}
