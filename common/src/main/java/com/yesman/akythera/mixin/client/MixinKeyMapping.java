package com.yesman.akythera.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.client.input.InputActivation;
import com.yesman.akythera.client.input.InputAction;
import com.yesman.akythera.client.input.InputManager;
import com.yesman.akythera.mixinaccessor.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class MixinKeyMapping implements KeyMappingAccessor {
    /// Actions bound to this key, indexed by [InputActivation#ordinal()]. `null` until the first bind.
    @Unique
    @Nullable
    private InputAction[] akythera$boundActions;

    /// Occupied slots in [#akythera$boundActions].
    @Unique
    private int akythera$boundCount;

    /// Consecutive ticks the key has been held; `0` up, `1` on the press tick, then `++` per tick.
    ///
    /// Counted here per client tick, **not** in `setDown`: vanilla fires `setDown(true)` on the GLFW
    /// press *and on every OS auto-repeat* (`action == 2`, ~500 ms then ~30 ms cadence), so counting
    /// in `setDown` would track the keyboard repeat rate, not the 20 Hz tick that `DOWN`/`HOLD` need.
    @Unique
    private int akythera$holdTicks;

    /// Down state on the previous tick, for edge detection.
    @Unique
    private boolean akythera$prevDown;

    /// Pending discrete releases drained by [#akythera$consumeRelease()].
    @Unique
    private int akythera$releaseCount;

    @Shadow
    private boolean isDown;

    @Shadow
    protected InputConstants.Key key;

    /// At HEAD `isDown` still holds the previous value, so `!down && isDown` is a real release edge.
    @Inject(at = @At(value = "HEAD"), method = "setDown(Z)V")
    public void akythera$onSetDown(boolean down, CallbackInfo callbackInfo) {
        if (!down && isDown) {
            akythera$releaseCount++;
        }
    }

    @Override
    public void akythera$bindAction(final InputAction action) {
        if (akythera$boundActions == null) {
            akythera$boundActions = new InputAction[InputActivation.values().length];
        }

        final int slot = action.inputActivation().ordinal();

        if (akythera$boundActions[slot] == null) {
            akythera$boundCount++;
        }

        akythera$boundActions[slot] = action;
    }

    @Override
    public boolean akythera$unbindAction(final InputAction action) {
        if (akythera$boundActions == null) {
            return true;
        }

        final int slot = action.inputActivation().ordinal();

        if (akythera$boundActions[slot] == action) {
            akythera$boundActions[slot] = null;
            akythera$boundCount--;
        }

        return akythera$boundCount == 0;
    }

    /// Runs the [InputActivation] timing table once per client tick; driven by [MixinMinecraft].
    @Unique
    @Override
    public void akythera$dispatchInputActions() {
        if (akythera$boundActions == null) {
            return;
        }

        final InputManager manager = InputManager.getInstance();
        final boolean down = this.isDown;
        final boolean pressEdge = down && !akythera$prevDown;
        final boolean releaseEdge = !down && akythera$prevDown;

        if (down) {
            akythera$holdTicks = pressEdge ? 1 : akythera$holdTicks + 1;
        }

        if (pressEdge) {
            akythera$fire(manager, InputActivation.PRESS);
        }

        if (down) {
            akythera$fireThresholded(manager);
        }

        if (releaseEdge) {
            akythera$fire(manager, InputActivation.RELEASE);
            akythera$fireReleaseThresholded(manager);
            akythera$holdTicks = 0;
        }

        akythera$prevDown = down;
    }

    /// Fires held-state [InputActivation] (DOWN / START / HOLD) by threshold.
    @Unique
    private void akythera$fireThresholded(final InputManager manager) {
        final InputAction down = akythera$action(InputActivation.DOWN);
        if (down != null && akythera$holdTicks <= akythera$threshold(down)) {
            manager.runEvent(down);
        }

        final InputAction start = akythera$action(InputActivation.START);
        if (start != null && akythera$holdTicks == akythera$threshold(start) + 1) {
            manager.runEvent(start);
        }

        final InputAction hold = akythera$action(InputActivation.HOLD);
        if (hold != null && akythera$holdTicks > akythera$threshold(hold)) {
            manager.runEvent(hold);
        }
    }

    /// Fires release [InputActivation] (CANCEL / COMPLETE) by threshold.
    @Unique
    private void akythera$fireReleaseThresholded(final InputManager manager) {
        final int lastHold = akythera$holdTicks;

        final InputAction cancel = akythera$action(InputActivation.CANCEL);
        if (cancel != null && lastHold <= akythera$threshold(cancel)) {
            manager.runEvent(cancel);
        }

        final InputAction complete = akythera$action(InputActivation.COMPLETE);
        if (complete != null && lastHold > akythera$threshold(complete)) {
            manager.runEvent(complete);
        }
    }

    @Unique
    private void akythera$fire(final InputManager manager, final InputActivation activation) {
        final InputAction action = akythera$action(activation);

        if (action != null) {
            manager.runEvent(action);
        }
    }

    @Unique
    private @Nullable InputAction akythera$action(final InputActivation activation) {
        return akythera$boundActions == null ? null : akythera$boundActions[activation.ordinal()];
    }

    /// `null` threshold is treated as 0 (no charge window).
    @Unique
    private int akythera$threshold(final InputAction action) {
        final Integer t = action.threshold();
        return t == null ? 0 : t;
    }

    @Override
    public int akythera$getHoldTick() {
        return akythera$holdTicks;
    }

    @Override
    public boolean akythera$consumeRelease() {
        if (this.akythera$releaseCount == 0) {
            return false;
        }
        --this.akythera$releaseCount;
        return true;
    }

    @Override
    public InputConstants.Key akythera$getKey() {
        return key;
    }
}
