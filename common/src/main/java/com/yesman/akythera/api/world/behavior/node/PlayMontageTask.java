package com.yesman.akythera.api.world.behavior.node;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.behavior.BehaviorTask;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import net.minecraft.resources.ResourceKey;

/// Plays [#montage]. Succeeds when the montage reaches its end time, and fails when another montage replaces it, a
/// stop request fades it out or the owner has no armature. Canceling the task fades the montage out.
public final class PlayMontageTask implements BehaviorTask {
    private final ResourceKey<AnimationMontage> montage;

    /// The section the montage starts from. Empty starts from the beginning.
    private final String startSection;

    /// [TaskStatus#RUNNING] until the montage play this task started ends.
    private TaskStatus status = TaskStatus.RUNNING;

    public PlayMontageTask(final ResourceKey<AnimationMontage> montage, final String startSection) {
        this.montage = montage;
        this.startSection = startSection;
    }

    @Override
    public TaskStatus doTask(final BrainComponent brain) {
        ArmatureComponent armatureComponent = brain.getOwner().getArmatureComponent();
        AnimationMontagePlayer.MontageCallbackHandle handle = armatureComponent == null
            ? null
            : armatureComponent.getAnimator().playAnimMontage(montage, startSection);

        if (handle == null) return TaskStatus.FAILED;

        status = TaskStatus.RUNNING;
        handle.addMontageCompletedCallback((_, _, interrupted) -> status = interrupted ? TaskStatus.FAILED : TaskStatus.SUCCEEDED);

        return status;
    }

    @Override
    public TaskStatus tickTask(final BrainComponent brain) {
        return status;
    }

    @Override
    public void onCanceled(final BrainComponent brain) {
        ArmatureComponent armatureComponent = brain.getOwner().getArmatureComponent();

        if (status == TaskStatus.RUNNING && armatureComponent != null) armatureComponent.getAnimator().stopPlayingMontage(montage);
    }
}
