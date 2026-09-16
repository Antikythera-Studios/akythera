package com.yesman.akythera.client.input;

import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.api.input.TriggeringState;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.client.KeyMapping;
import org.jspecify.annotations.Nullable;

/// One ability's key binding, written as a static nested class on the ability itself.
///
/// The callbacks run before the activation packet leaves.
@ClientOnly
public interface AbilityInputInterface {
    /// The key this ability reads. Null leaves the ability unbound.
    @Nullable KeyMapping getKeyMapping();

    void onTriggered(AbilitySystemComponent component, AbilityInstance abilityInstance);

    /// The key was let go at or above the threshold.
    default void onCompleted(
        final AbilitySystemComponent component,
        final AbilityInstance abilityInstance
    ) {

    }

    /// The key was let go below the threshold.
    default void onCanceled(
        final AbilitySystemComponent component,
        final AbilityInstance abilityInstance
    ) {

    }

    /// Runs the callback `triggeringState` names.
    default void dispatch(
        final TriggeringState triggeringState,
        final AbilitySystemComponent component,
        final AbilityInstance abilityInstance
    ) {
        switch (triggeringState) {
            case TRIGGERED -> onTriggered(component, abilityInstance);
            case COMPLETED -> onCompleted(component, abilityInstance);
            case CANCELED -> onCanceled(component, abilityInstance);
        }
    }

    /// Builds the interface `inputInterfaceClass` names, or null when it has no usable no-argument
    /// constructor.
    static @Nullable AbilityInputInterface instantiate(
        final Class<? extends AbilityInputInterface> inputInterfaceClass
    ) {
        try {
            return inputInterfaceClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            Akythera.LOGGER.error(
                "Ability input interface {} needs a public no-argument constructor",
                inputInterfaceClass.getName(), exception
            );

            return null;
        }
    }

    /// The key `inputAction` names, or null when no mod registered one under that name.
    static @Nullable KeyMapping resolveKeyMapping(final InputAction inputAction) {
        KeyMapping keyMapping = KeyMapping.get(inputAction.keyMapping());

        if (keyMapping == null) {
            Akythera.LOGGER.warn("No key mapping is registered as {}", inputAction.keyMapping());
        }

        return keyMapping;
    }
}
