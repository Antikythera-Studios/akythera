package com.yesman.akythera.api.world.ability.impl;

import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTask;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/// A task that accompanies the lifecycle with a played montage.
///
/// Completes when the montage reaches its end time. Interrupted when another montage replaces it or a stop
/// request fades it out.
public final class PlayMontageTask extends AbilityTask {
    private final ResourceKey<AnimationMontage> montageId;
    private final String startSection;

    /// Whether the montage play this task started is over.
    private boolean ended;

    /// Whether the montage play was cut before its end time. Read once [#ended].
    private boolean interrupted;

    public PlayMontageTask(final ResourceKey<AnimationMontage> montageId, final String startSection) {
        this.montageId = montageId;
        this.startSection = startSection;
    }

    @Override
    public void onStart(final AbilitySystemComponent abilitySystemComponent) {
        ArmatureComponent armatureComponent = abilitySystemComponent.getOwner().getArmatureComponent();

        if (armatureComponent == null) {
            Akythera.crashInDevOrWarnInProd("Can't start " + PlayMontageTask.class.getSimpleName() + ": "
                + abilitySystemComponent.getOwner() + " doesn't have armature component ");
        }

        AnimationMontagePlayer.@Nullable MontageCallbackHandle handle = armatureComponent == null
            ? null
            : armatureComponent.getAnimator().playAnimMontage(montageId, startSection);

        // Nothing plays, so the task stops as interrupted on its first tick.
        if (handle == null) {
            ended = true;
            interrupted = true;

            return;
        }

        handle.addMontageCompletedCallback((_, _, wasInterrupted) -> {
            ended = true;
            interrupted = wasInterrupted;
        });
    }

    @Override
    public void tick(final AbilitySystemComponent abilitySystemComponent) {
    }

    @Override
    public boolean isEnd(final AbilitySystemComponent abilitySystemComponent) {
        return ended && !interrupted;
    }

    @Override
    public boolean canContinue(final AbilitySystemComponent abilitySystemComponent) {
        return super.canContinue(abilitySystemComponent) && !(ended && interrupted);
    }

    /// Fades the montage out when the task is stopped while it still plays.
    @Override
    public void onCanceled(final AbilitySystemComponent abilitySystemComponent) {
        ArmatureComponent armatureComponent = abilitySystemComponent.getOwner().getArmatureComponent();

        if (!ended && armatureComponent != null) armatureComponent.getAnimator().stopPlayingMontage(montageId);

        super.onCanceled(abilitySystemComponent);
    }
}
