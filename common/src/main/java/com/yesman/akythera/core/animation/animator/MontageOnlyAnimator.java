package com.yesman.akythera.core.animation.animator;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/// An animator that only accommodates [AnimationMontage]. Mainly to optimize
/// logical server entities whose don't need to sync locomotion.
///
/// Some exceptional cases, like entities having partitional OBB for each bone,
/// need to use [BlendingAnimator] in servers too.
public final class MontageOnlyAnimator implements Animator {
    private final AnimationMontagePlayer montagePlayer = new AnimationMontagePlayer();
    private ComponentAttachable owner;
    private @Nullable ArmatureComponent armatureComponent;

    public boolean isHardPaused = false;
    public boolean isSoftPaused = false;

    public MontageOnlyAnimator(final ComponentAttachable owner) {
        this.owner = owner;
    }

    @Override
    public @NonNull AnimationMontagePlayer getMontagePlayer(final ResourceKey<@NonNull AnimationMontage> animMontageKey) {
        return montagePlayer;
    }

    @Override
    public Collection<AnimationMontagePlayer> getMontagePlayers() {
        return List.of(montagePlayer);
    }

    @Override
    public void update(final float timeFrame, final float partialTick) {
        montagePlayer.update(timeFrame);
    }

    @Override
    public void tick() {
        montagePlayer.tick(owner);
    }

    @Override
    public void setSoftPause(final boolean paused) {
        this.isSoftPaused = paused;
    }

    @Override
    public void setHardPause(final boolean paused) {
        this.isHardPaused = paused;
    }

    @Override
    public boolean isSoftPaused() {
        return isSoftPaused;
    }

    @Override
    public boolean isHardPaused() {
        return isHardPaused;
    }

    @Override
    public Pose getPose() {
        return montagePlayer.getPose();
    }

    @SuppressWarnings("unchecked") @Override
    public <O> O getOwner() {
        return (O)owner;
    }

    @Override
    public void setOwner(final ComponentAttachable owner) {
        this.owner = owner;
    }

    @Override
    public @Nullable ArmatureComponent getArmatureComponent() {
        return armatureComponent;
    }

    @Override
    public void setArmatureComponent(final ArmatureComponent armatureComponent) {
        this.armatureComponent = armatureComponent;
    }
}
