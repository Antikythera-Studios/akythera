package com.yesman.akythera.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesman.akythera.api.input.TriggeringState;
import com.yesman.akythera.api.input.TriggeringType;
import com.yesman.akythera.api.input.InputAction;
import com.yesman.akythera.client.input.InputManager;
import com.yesman.akythera.mixinaccessor.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mixin(KeyMapping.class)
public abstract class MixinKeyMapping implements KeyMappingAccessor {
    /// Actions bound to this key, grouped by [TriggeringType] and ordered by [InputAction#priority]
    /// within each group. Null until the first bind.
    @Unique
    @Nullable
    private Map<TriggeringType, List<Holder<InputAction>>> akythera$boundActions;

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
    public void akythera$bindAction(final Holder<InputAction> action) {
        if (akythera$boundActions == null) {
            akythera$boundActions = new EnumMap<>(TriggeringType.class);
        }

        List<Holder<InputAction>> bound = akythera$boundActions.computeIfAbsent(
            action.value().triggeringType(), type -> new ArrayList<>()
        );

        if (bound.contains(action)) return;

        bound.add(action);
        bound.sort(Comparator.comparingInt((Holder<InputAction> held) -> held.value().priority()).reversed());
        akythera$boundCount++;
    }

    @Override
    public boolean akythera$unbindAction(final Holder<InputAction> action) {
        if (akythera$boundActions == null) {
            return true;
        }

        List<Holder<InputAction>> bound = akythera$boundActions.get(action.value().triggeringType());

        if (bound != null && bound.remove(action)) {
            akythera$boundCount--;
        }

        if (akythera$boundCount > 0) return false;

        // The key stops being dispatched here, so its edge state would otherwise be read again by
        // whatever binds to it next: a held key would look like a release it never saw.
        akythera$prevDown = false;
        akythera$holdTicks = 0;

        return true;
    }

    /// Runs the [TriggeringType] timing table once per client tick; driven by [MixinMinecraft].
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
            akythera$fire(manager, TriggeringType.PRESS, TriggeringState.TRIGGERED);
        }

        if (down) {
            akythera$fire(manager, TriggeringType.DOWN, TriggeringState.TRIGGERED);
            akythera$fire(manager, TriggeringType.HOLD, TriggeringState.TRIGGERED);
            akythera$fire(manager, TriggeringType.HOLD_UNTIL, TriggeringState.TRIGGERED);
        }

        if (releaseEdge) {
            akythera$fire(manager, TriggeringType.RELEASE, TriggeringState.TRIGGERED);
            akythera$fire(manager, TriggeringType.HOLD, TriggeringState.COMPLETED);
            akythera$fireHoldUntilRelease(manager);
            akythera$holdTicks = 0;
        }

        akythera$prevDown = down;
    }

    /// A Hold Until release completes at or above its threshold and cancels below it, so each bound
    /// action is judged against its own.
    @Unique
    private void akythera$fireHoldUntilRelease(final InputManager manager) {
        List<Holder<InputAction>> bound = akythera$boundActions.get(TriggeringType.HOLD_UNTIL);

        if (bound == null) return;

        final int lastHold = akythera$holdTicks;

        for (Holder<InputAction> action : new ArrayList<>(bound)) {
            manager.runEvent(
                action,
                action.value().reachedThreshold(lastHold)
                    ? TriggeringState.COMPLETED
                    : TriggeringState.CANCELED
            );
        }
    }

    @Unique
    private void akythera$fire(
        final InputManager manager,
        final TriggeringType triggeringType,
        final TriggeringState triggeringState
    ) {
        List<Holder<InputAction>> bound = akythera$boundActions.get(triggeringType);

        if (bound == null) return;

        for (Holder<InputAction> action : new ArrayList<>(bound)) {
            manager.runEvent(action, triggeringState);
        }
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
