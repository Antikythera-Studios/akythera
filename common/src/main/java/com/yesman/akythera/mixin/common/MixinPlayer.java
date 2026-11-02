package com.yesman.akythera.mixin.common;

import com.yesman.akythera.api.world.patch.entity.PlayerPatch;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Player.class)
public abstract class MixinPlayer {
    /// Skips the sweep while [PlayerPatch#hurtTarget] runs without vanilla attack effects.
    @Inject(at = @At(value = "HEAD"), method = "isSweepAttack(ZZZ)Z", cancellable = true)
    private void akythera$skipSweep(final boolean fullStrengthAttack, final boolean criticalAttack, final boolean knockbackAttack, final CallbackInfoReturnable<Boolean> callbackInfo) {
        if (akythera$withoutVanillaAttackEffects()) callbackInfo.setReturnValue(false);
    }

    /// Keeps the attack strength while [PlayerPatch#hurtTarget] runs without vanilla attack effects.
    @Redirect(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;resetOnlyAttackStrengthTicker()V"),
        method = "onAttack()V"
    )
    private void akythera$keepAttackStrength(final Player self) {
        if (!akythera$withoutVanillaAttackEffects()) self.resetOnlyAttackStrengthTicker();
    }

    @Unique
    private boolean akythera$withoutVanillaAttackEffects() {
        return ((EntityAccessor)this).akythera$getEntityPatch() instanceof PlayerPatch patch && !patch.hasVanillaAttackEffects();
    }
}
