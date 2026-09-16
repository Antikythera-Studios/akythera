package com.yesman.akythera.client.input;

import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.input.TriggeringState;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.ActivationType;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.serverbound.ServerBoundAbilityTrigger;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;

/// Turns one key's [TriggeringState]s into the single activation packet, for the ability in `slot`.
///
/// The packet carries no verdict. This decides only whether to send, which the preset answers.
@ClientOnly
public final class AbilityInputBridge implements InputListener {
    private final AbilitySystemComponent component;

    private final AbilitySlot slot;

    private final ActivationType activationType;

    /// The ability's own callbacks, run before the packet.
    private final @Nullable AbilityInputInterface inputInterface;

    /// Whether a trigger has already been sent during the current hold.
    private boolean sentDuringHold;

    public AbilityInputBridge(
        final AbilitySystemComponent component,
        final AbilitySlot slot,
        final ActivationType activationType,
        final @Nullable AbilityInputInterface inputInterface
    ) {
        this.component = component;
        this.slot = slot;
        this.activationType = activationType;
        this.inputInterface = inputInterface;
    }

    @Override
    public void onTriggered(final InputAction inputAction, final TriggeringState triggeringState) {
        if (!activationType.accepts(inputAction.triggeringType())) return;

        // A binding outlives the world it was made in until its slot is equipped again, and the
        // component it holds then belongs to a player who is gone.
        if (component.getOwner().getOriginal() != Minecraft.getInstance().player) {
            AbilityInputBinder.unbind(slot);

            return;
        }

        AbilityInstance instance = component.getInstance(slot);

        if (instance == null) return;

        if (inputInterface != null) inputInterface.dispatch(triggeringState, component, instance);

        if (activationType.emitsTriggerPacket(triggeringState, sentDuringHold)) {
            NetworkingHelper.getInstance()
                .sendToServer(new ServerBoundAbilityTrigger(slot, localInput(instance)));

            sentDuringHold = triggeringState == TriggeringState.TRIGGERED;
        }

        if (triggeringState != TriggeringState.TRIGGERED) sentDuringHold = false;
    }

    /// `instance`'s state through its server bound codec, or empty.
    private byte[] localInput(final AbilityInstance instance) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) return new byte[0];

        return AbilityState.encode(
            instance.getState(), PacketFlow.SERVERBOUND, player.level().registryAccess()
        );
    }

    public AbilitySlot getSlot() {
        return slot;
    }
}
