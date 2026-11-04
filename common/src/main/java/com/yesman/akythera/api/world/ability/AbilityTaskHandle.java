package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// A reference to one running [AbilityTask].
///
/// Goes invalid when the task terminates, when the ability that started it is unequipped, and when
/// [#invalidate] is called. Server side only, never synced.
@ScriptType
public final class AbilityTaskHandle {
    private final AbilitySystemComponent abilitySystemComponent;
    private final AbilityTask task;

    /// Run after [AbilityTask#onEnd] when the task finishes its own work.
    private final @Nullable Runnable onCompleted;

    /// Run after [AbilityTask#onCanceled] when the task is stopped before finishing.
    private final @Nullable Runnable onInterrupted;

    private boolean valid = true;

    @ApiStatus.Internal
    AbilityTaskHandle(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityTask task,
        final @Nullable Runnable onCompleted,
        final @Nullable Runnable onInterrupted
    ) {
        this.abilitySystemComponent = abilitySystemComponent;
        this.task = task;
        this.onCompleted = onCompleted;
        this.onInterrupted = onInterrupted;
    }

    /// Whether the task is still running and this handle still controls it.
    public boolean isValid() {
        return valid;
    }

    /// The task this points at. Reading it once [#isValid] is false reports the terminated state.
    public AbilityTask getTask() {
        return task;
    }

    /// Stops the task as interrupted, firing [AbilityTask#onCanceled] before this call returns.
    /// Does nothing once this handle is invalid.
    ///
    /// @return whether this call stopped it
    public boolean cancel() {
        if (!valid) return false;

        abilitySystemComponent.cancelTask(this);

        return true;
    }

    /// Lets go of the task. The task runs on and still ends with its ability, but [#onCompleted] and
    /// [#onInterrupted] never run.
    @ScriptFunction(
        label = "Invalidate Ability Task Handle", category = "Ability/Task",
        desc = "Lets go of the task without stopping it. The task keeps running until it finishes or its ability ends, and its completed and interrupted callbacks never run. The handle can no longer cancel the task."
    )
    public void invalidate() {
        valid = false;
    }

    /// Runs [#onInterrupted] when `canceled`, [#onCompleted] otherwise.
    @ApiStatus.Internal
    void runCallback(final boolean canceled) {
        Runnable callback = canceled ? onInterrupted : onCompleted;

        if (callback != null) callback.run();
    }
}
