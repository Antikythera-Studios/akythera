package com.yesman.akythera.api.world.ability;

public abstract class Ability<S extends AbilityState> {
    public void tryActivate(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

    public abstract void onActivated(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );

    public abstract void onRecast(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );

    public abstract void onCanceled(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );

    public abstract void onFinished(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    );
}
