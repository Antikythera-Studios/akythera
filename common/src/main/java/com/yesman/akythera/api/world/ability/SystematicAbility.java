package com.yesman.akythera.api.world.ability;

public class SystematicAbility<S extends AbilityState> extends Ability<S> {



    @Override
    public void onActivated(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

    @Override
    public void onRecast(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

    @Override
    public void onCanceled(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }

    @Override
    public void onFinished(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final S abilityState
    ) {

    }
}
