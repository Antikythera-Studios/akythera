package com.yesman.akythera.api.world.ability;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// One equipped ability on one [AbilitySystemComponent]: the shared [Ability], the slot it fills,
/// and the per-owner state its callbacks read.
///
/// Drives only what every ability shares: the activation lifecycle.
public final class AbilityInstance {
    private @Nullable Ability<?> ability;
    private @Nullable AbilitySlot slot;

    /// Built by [Ability#createState] when the ability is equipped.
    private AbilityState state = AbilityState.EMPTY;

    /// The last state sent to clients.
    private AbilityState prevState = AbilityState.EMPTY;

    /// Ticks the activation input has been held. Server side only.
    private int holdingTicks;

    /// Set once this instance's state threw while encoding, which latches its state sync off.
    private boolean stateSyncFailed;

    private boolean activated;

    /// Re-points this instance and rebuilds its state.
    public void setAbility(final Ability<?> ability) {
        this.ability = ability;
        state = ability.createState();
        prevState = ability.createState();
    }

    public @Nullable Ability<?> getAbility() {
        return ability;
    }

    public @Nullable AbilitySlot getSlot() {
        return slot;
    }

    public void setSlot(final AbilitySlot slot) {
        this.slot = slot;
    }

    public AbilityState getState() {
        return state;
    }

    public AbilityState getPrevState() {
        return prevState;
    }

    public int getHoldingTicks() {
        return holdingTicks;
    }

    public boolean isStateSyncFailed() {
        return stateSyncFailed;
    }

    @ApiStatus.Internal
    public void setStateSyncFailed(final boolean stateSyncFailed) {
        this.stateSyncFailed = stateSyncFailed;
    }

    public void setHoldingTicks(final int holdingTicks) {
        this.holdingTicks = holdingTicks;
    }

    public boolean isActivated() {
        return activated;
    }

    /// Binds this instance to `ability` in `slot` and builds its state.
    public void equip(final AbilitySystemComponent abilitySystemComponent, final Ability<?> ability, final AbilitySlot slot) {
        this.ability = ability;
        this.slot = slot;
        state = ability.createState();
        prevState = ability.createState();
        activated = false;
        holdingTicks = 0;
        stateSyncFailed = false;

        typed().onEquipped(abilitySystemComponent, this, state);
    }

    /// Releases this instance's ability, cancelling it first when it is still running.
    public void unequip(final AbilitySystemComponent abilitySystemComponent) {
        if (ability == null) return;

        cancelAbility(abilitySystemComponent);
        typed().onUnequipped(abilitySystemComponent, this, state);

        ability = null;
        slot = null;
        state = AbilityState.EMPTY;
        prevState = AbilityState.EMPTY;
    }

    /// Activates the ability, or recasts it when it is already running.
    ///
    /// @return whether the ability activated or recast
    public boolean tryActivate(final AbilitySystemComponent abilitySystemComponent) {
        if (ability == null) return false;

        if (activated) return recastAbility(abilitySystemComponent);

        if (!typed().canActivate(abilitySystemComponent, this, state)) return false;

        activated = true;
        typed().onActivated(abilitySystemComponent, this, state);

        return true;
    }

    /// Marks this activated and runs [Ability#onActivated], skipping [Ability#canActivate].
    ///
    /// A client's cooldown never banks a charge, so it cannot re-run the server's decision.
    @ApiStatus.Internal
    public void applyActivated(final AbilitySystemComponent abilitySystemComponent) {
        if (ability == null || activated) return;

        activated = true;
        typed().onActivated(abilitySystemComponent, this, state);
    }

    /// Runs [Ability#onRecast] without re-deciding anything.
    @ApiStatus.Internal
    public void applyRecast(final AbilitySystemComponent abilitySystemComponent) {
        if (ability == null || !activated) return;

        typed().onRecast(abilitySystemComponent, this, state);
    }

    /// Re-requests an ability that is already running.
    ///
    /// @return whether the ability was running
    public boolean recastAbility(final AbilitySystemComponent abilitySystemComponent) {
        if (ability == null || !activated) return false;

        typed().onRecast(abilitySystemComponent, this, state);

        return true;
    }

    /// Stops the ability as interrupted.
    ///
    /// @return whether the ability was running
    public boolean cancelAbility(final AbilitySystemComponent abilitySystemComponent) {
        if (ability == null || !activated) return false;

        activated = false;
        typed().onCanceled(abilitySystemComponent, this, state);

        return true;
    }

    /// Stops the ability as completed.
    ///
    /// @return whether the ability was running
    public boolean endAbility(final AbilitySystemComponent abilitySystemComponent) {
        if (ability == null || !activated) return false;

        activated = false;
        typed().onFinished(abilitySystemComponent, this, state);

        return true;
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// [#state] is always what the current [#ability] built through [Ability#createState].
    @SuppressWarnings("unchecked")
    private Ability<AbilityState> typed() {
        return (Ability<AbilityState>)ability;
    }
}
