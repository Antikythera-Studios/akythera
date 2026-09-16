package com.yesman.akythera.api.world.ability;

import org.jetbrains.annotations.ApiStatus;

/// A reference to one running [AbilityTask].
///
/// Goes invalid when the task terminates and when the ability that started it is unequipped. Server
/// side only, never synced.
public final class AbilityTaskHandle {
    private final AbilitySystemComponent abilitySystemComponent;
    private final AbilityTask task;

    private boolean valid = true;

    @ApiStatus.Internal
    AbilityTaskHandle(final AbilitySystemComponent abilitySystemComponent, final AbilityTask task) {
        this.abilitySystemComponent = abilitySystemComponent;
        this.task = task;
    }

    /// Whether the task is still running.
    public boolean isValid() {
        return valid;
    }

    /// The task this points at. Reading it once [#isValid] is false reports the terminated state.
    public AbilityTask getTask() {
        return task;
    }

    /// Stops the task as interrupted, firing [AbilityTask#onCanceled] before this call returns.
    /// Does nothing once the task has already terminated.
    ///
    /// @return whether this call stopped it
    public boolean cancel() {
        if (!valid) return false;

        abilitySystemComponent.cancelTask(this);

        return true;
    }

    @ApiStatus.Internal
    void invalidate() {
        valid = false;
    }
}
