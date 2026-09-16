package com.yesman.akythera.client.input;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/// Binds the local player's equipped abilities to their keys, one [AbilityInputBridge] per filled
/// slot.
///
/// Only the local player is bound: a key press is the one input no other entity has.
@ClientOnly
public final class AbilityInputBinder {
    /// What each slot bound, so unequipping releases the same listener it registered.
    private static final Map<AbilitySlot, Binding> BINDINGS = new LinkedHashMap<>();

    private record Binding(Holder<InputAction> action, AbilityInputBridge bridge) {}

    private AbilityInputBinder() {}

    /// Releases every binding when the client leaves the world it bound them in.
    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(event -> unbindAll());
    }

    /// Rebinds `slot` to whatever the ability now in it reads. An ability that names no
    /// [InputAction] leaves the slot unbound.
    @ApiStatus.Internal
    public static void bind(
        final AbilitySystemComponent component,
        final AbilitySlot slot,
        final AbilityInstance instance
    ) {
        unbind(slot);

        Ability<?> ability = instance.getAbility();

        if (!(ability instanceof SystematicAbility<?> systematic)) return;

        Holder<InputAction> action = systematic.getInputAction();

        if (action == null) return;

        AbilityInputInterface inputInterface = buildInterface(ability);
        KeyMapping keyMapping = inputInterface == null
            ? AbilityInputInterface.resolveKeyMapping(action.value())
            : inputInterface.getKeyMapping();

        if (keyMapping == null) return;

        AbilityInputBridge bridge = new AbilityInputBridge(
            component, slot, systematic.getActivationType(), inputInterface
        );

        InputManager.getInstance().register(action, keyMapping, bridge);
        BINDINGS.put(slot, new Binding(action, bridge));
    }

    /// Releases `slot`'s key.
    @ApiStatus.Internal
    public static void unbind(final AbilitySlot slot) {
        Binding binding = BINDINGS.remove(slot);

        if (binding == null) return;

        InputManager.getInstance().unregister(binding.action(), binding.bridge());
    }

    /// Releases every key.
    @ApiStatus.Internal
    public static void unbindAll() {
        for (AbilitySlot slot : Map.copyOf(BINDINGS).keySet()) {
            unbind(slot);
        }
    }

    private static @Nullable AbilityInputInterface buildInterface(final Ability<?> ability) {
        Class<? extends AbilityInputInterface> inputInterfaceClass =
            ability.getAbilityInputInterfaceClass();

        return inputInterfaceClass == null
            ? null
            : AbilityInputInterface.instantiate(inputInterfaceClass);
    }
}
