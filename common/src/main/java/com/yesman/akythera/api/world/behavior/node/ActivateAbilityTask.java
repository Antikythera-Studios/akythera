package com.yesman.akythera.api.world.behavior.node;

import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import org.jspecify.annotations.Nullable;

/// Activates the ability in [#slot] of the owner's [AbilitySystemComponent]. Succeeds when the ability finishes, and
/// fails when the activation is refused or the ability is canceled. Canceling the task cancels the ability.
public final class ActivateAbilityTask implements BehaviorTask {
    private final AbilitySlot slot;

    /// The instance this task activated, or null when it activated none.
    private @Nullable AbilityInstance instance;

    public ActivateAbilityTask(final AbilitySlot slot) {
        this.slot = slot;
    }

    @Override
    public TaskStatus doTask(final BrainComponent brain) {
        AbilitySystemComponent abilitySystem = brain.getOwner().getComponent(AbilitySystemComponent.class);

        instance = abilitySystem == null ? null : abilitySystem.getInstance(slot);

        if (instance == null || !abilitySystem.activateAbility(slot)) return TaskStatus.FAILED;

        return tickTask(brain);
    }

    @Override
    public TaskStatus tickTask(final BrainComponent brain) {
        if (instance == null) return TaskStatus.FAILED;
        if (instance.isActivated()) return TaskStatus.RUNNING;

        return instance.isLastRunCanceled() ? TaskStatus.FAILED : TaskStatus.SUCCEEDED;
    }

    @Override
    public void onCanceled(final BrainComponent brain) {
        AbilitySystemComponent abilitySystem = brain.getOwner().getComponent(AbilitySystemComponent.class);

        if (abilitySystem != null && instance != null && instance.isActivated()) abilitySystem.cancelAbility(slot);
    }
}
