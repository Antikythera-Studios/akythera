package com.yesman.akythera.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.mixinaccessor.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class MixinKeyMapping implements KeyMappingAccessor {
    @Unique
    private int epicfight$holdTicks;

    @Unique
    private int releaseCount;

    @Shadow
    private boolean isDown;

    @Shadow
    private InputConstants.Key key;

    @Inject(at = @At(value = "HEAD"), method = "setDown(Z)V")
    public void epicfight$setDown(boolean down, CallbackInfo callbackInfo) {
        if (down) {
            epicfight$holdTicks++;
        } else if (isDown) {
            epicfight$holdTicks = 0;
            --releaseCount;
        }
    }

    @Override
    public int akythera$getHoldTick() {
        return epicfight$holdTicks;
    }

    @Override
    public boolean akythera$consumeRelease() {
        if (this.releaseCount == 0) {
            return false;
        } else {
            --this.releaseCount;
            return true;
        }
    }

    @Override
    public InputConstants.Key akythera$getKey() {
        return key;
    }
}
