package com.yesman.akythera.api.world.ability;

public final class AbilityInstance {
    private Ability<?> ability;

    public void setAbility(final Ability<?> ability) {
        this.ability = ability;
    }

    public Ability<?> getAbility() {
        return ability;
    }
}
