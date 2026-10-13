package com.yesman.akythera.api.world.ability.impl;

import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTask;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import net.minecraft.resources.ResourceKey;

/// A task that accompanies the lifecycle with a played montage
public final class PlayMontageTask extends AbilityTask {
    private final ResourceKey<AnimationMontage> montageId;
    private final String startSection;

    public PlayMontageTask(final ResourceKey<AnimationMontage> montageId, final String startSection) {
        this.montageId = montageId;
        this.startSection = startSection;
    }

    @Override
    public void onStart(final AbilitySystemComponent abilitySystemComponent) {
        if (abilitySystemComponent.getOwner().getArmatureComponent() == null) {
            Akythera.crashInDevOrWarnInProd("Can't start " + PlayMontageTask.class.getSimpleName() + ": "
                + abilitySystemComponent.getOwner() + " doesn't have armature component ");
        }

        abilitySystemComponent.getOwner().getArmatureComponent().getAnimator().playAnimMontage(montageId, startSection);
    }

    @Override
    public void tick(final AbilitySystemComponent abilitySystemComponent) {
    }

    @Override
    public boolean isEnd(final AbilitySystemComponent abilitySystemComponent) {
        AnimationMontagePlayer montagePlayer = abilitySystemComponent.getOwner().getArmatureComponent().getAnimator().getMontagePlayer(montageId);
        return montagePlayer == null || montagePlayer.isFinished();
    }
}
