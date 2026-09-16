package com.yesman.akythera.api.world.ability;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/// One unit of work an [Ability] runs over time: play an animation and wait, count down, watch for
/// a hit. Server side only.
public abstract class AbilityTask {
    private @Nullable AbilityInstance owner;

    /// Whether the ability ending stops this task with it.
    private final boolean endsWithAbility;

    /// Run by [#onCanceled] when the task is stopped before finishing.
    private final @Nullable Runnable onInterrupted;

    /// Run by [#onEnd] when the task finishes its own work.
    private final @Nullable Runnable onCompleted;

    private boolean cancelRequested;

    protected AbilityTask() {
        this(true, null, null);
    }

    protected AbilityTask(final @Nullable Runnable onInterrupted, final @Nullable Runnable onCompleted) {
        this(true, onInterrupted, onCompleted);
    }

    protected AbilityTask(
        final boolean endsWithAbility,
        final @Nullable Runnable onInterrupted,
        final @Nullable Runnable onCompleted
    ) {
        this.endsWithAbility = endsWithAbility;
        this.onInterrupted = onInterrupted;
        this.onCompleted = onCompleted;
    }

    /// Called once when the task starts running.
    public abstract void onStart(final AbilitySystemComponent abilitySystemComponent);

    /// Called every server tick while the task runs.
    public abstract void tick(final AbilitySystemComponent abilitySystemComponent);

    /// Whether the task finished its work.
    public abstract boolean isEnd();

    /// Called when the task finished its own work.
    public void onEnd(final AbilitySystemComponent abilitySystemComponent) {
        if (onCompleted != null) onCompleted.run();
    }

    /// Called when the task was stopped before finishing.
    public void onCanceled(final AbilitySystemComponent abilitySystemComponent) {
        if (onInterrupted != null) onInterrupted.run();
    }

    /// Whether the task may keep running. Returning false stops it as canceled.
    public boolean canContinue(final AbilitySystemComponent abilitySystemComponent) {
        return !cancelRequested;
    }

    public boolean endsWithAbility() {
        return endsWithAbility;
    }

    /// The instance that started this task. Null until it is.
    public @Nullable AbilityInstance getOwner() {
        return owner;
    }

    @ApiStatus.Internal
    void bind(final AbilityInstance owner) {
        this.owner = owner;
    }

    @ApiStatus.Internal
    void requestCancel() {
        cancelRequested = true;
    }
}
