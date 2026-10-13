package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.gui.HudManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Draws and ticks the [HudManager] HUDs along with the vanilla HUD.
@Mixin(Hud.class)
public abstract class MixinHud {
    @Shadow
    public abstract boolean isHidden();

    @Inject(at = @At("TAIL"), method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
    private void akythera$extractHuds(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        if (!isHidden()) HudManager.getInstance().extractRenderState(graphics, deltaTracker);
    }

    @Inject(at = @At("TAIL"), method = "tick()V")
    private void akythera$tickHuds(CallbackInfo callbackInfo) {
        HudManager.getInstance().tick();
    }
}
