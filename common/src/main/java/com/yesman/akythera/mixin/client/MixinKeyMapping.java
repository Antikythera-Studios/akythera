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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

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

    /// Bound actions suppressing this key through [InputManager#suppressInputKey].
    @Unique
    private final Set<Holder<InputAction>> akythera$suppressedActions = new HashSet<>();

    /// The highest [InputAction#priority] among the bound actions that [InputAction#suppressLowerActions] or sit in
    /// [#akythera$suppressedActions], or [Integer#MIN_VALUE] while none does.
    @Unique
    private int akythera$suppressingPriority = Integer.MIN_VALUE;

    @Shadow
    private boolean isDown;

    @Shadow
    private int clickCount;

    @Shadow
    protected InputConstants.Key key;

    /// At HEAD `isDown` still holds the previous value, so `!down && isDown` is a real release edge.
    @Inject(at = @At(value = "HEAD"), method = "setDown(Z)V")
    public void akythera$onSetDown(boolean down, CallbackInfo callbackInfo) {
        if (!down && isDown) {
            akythera$releaseCount++;
        }
    }

    /// Fires the release of the keys held when a screen opens, since dispatch pauses while it is open.
    @Inject(at = @At(value = "TAIL"), method = "releaseAll()V")
    private static void akythera$dispatchReleaseOnScreenOpen(final CallbackInfo callbackInfo) {
        InputManager.getInstance().dispatchInputActions();
    }

    /// Hides the key's down state from vanilla while a bound action suppresses lower actions.
    @Inject(at = @At(value = "HEAD"), method = "isDown()Z", cancellable = true)
    private void akythera$hideDownFromVanilla(final CallbackInfoReturnable<Boolean> callbackInfo) {
        if (akythera$suppressingPriority != Integer.MIN_VALUE) callbackInfo.setReturnValue(false);
    }

    /// Drops the key's clicks while a bound action suppresses lower actions, so none is read once it stops.
    @Inject(at = @At(value = "HEAD"), method = "consumeClick()Z", cancellable = true)
    private void akythera$hideClicksFromVanilla(final CallbackInfoReturnable<Boolean> callbackInfo) {
        if (akythera$suppressingPriority == Integer.MIN_VALUE) return;

        clickCount = 0;
        callbackInfo.setReturnValue(false);
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
        akythera$refreshSuppressingPriority();
    }

    /// Recomputes [#akythera$suppressingPriority], canceling the held actions it newly suppresses while the key is held.
    @Unique
    private void akythera$refreshSuppressingPriority() {
        final int previous = akythera$suppressingPriority;
        akythera$suppressingPriority = akythera$highestSuppressingPriority(false);

        if (akythera$holdTicks == 0 || akythera$suppressingPriority <= previous) return;

        final InputManager manager = InputManager.getInstance();

        for (TriggeringType triggeringType : TriggeringType.values()) {
            List<Holder<InputAction>> bound = triggeringType.isHeld() ? akythera$boundActions.get(triggeringType) : null;

            if (bound == null) continue;

            for (Holder<InputAction> action : new ArrayList<>(bound)) {
                int priority = action.value().priority();

                if (priority >= previous && priority < akythera$suppressingPriority && action.value().startedHold(akythera$holdTicks)) {
                    manager.runEvent(action, TriggeringState.CANCELED);
                }
            }
        }
    }

    /// The highest priority among the suppressing bound actions, or [Integer#MIN_VALUE] while none does.
    /// `excludeHeld` leaves out the held actions.
    @Unique
    private int akythera$highestSuppressingPriority(final boolean excludeHeld) {
        return akythera$boundActions.values().stream()
            .flatMap(List::stream)
            .filter(held -> held.value().suppressLowerActions() || akythera$suppressedActions.contains(held))
            .filter(held -> !excludeHeld || !held.value().triggeringType().isHeld())
            .mapToInt(held -> held.value().priority())
            .max()
            .orElse(Integer.MIN_VALUE);
    }

    @Override
    public void akythera$suppressAction(final Holder<InputAction> action, final boolean suppress) {
        if (akythera$boundActions == null) return;

        List<Holder<InputAction>> bound = akythera$boundActions.get(action.value().triggeringType());

        if (suppress && bound != null && bound.contains(action)) {
            akythera$suppressedActions.add(action);
        } else {
            akythera$suppressedActions.remove(action);
        }

        akythera$refreshSuppressingPriority();
    }

    @Override
    public boolean akythera$unbindAction(final Holder<InputAction> action) {
        if (akythera$boundActions == null) {
            return true;
        }

        List<Holder<InputAction>> bound = akythera$boundActions.get(action.value().triggeringType());

        if (bound != null && bound.remove(action)) {
            akythera$boundCount--;
            akythera$suppressedActions.remove(action);
            akythera$refreshSuppressingPriority();
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
            akythera$fire(manager, TriggeringType.PRESS, TriggeringState.TRIGGERED, akythera$suppressingPriority);
        }

        if (down) {
            akythera$fire(manager, TriggeringType.DOWN, TriggeringState.TRIGGERED, akythera$suppressingPriority);
            akythera$fire(manager, TriggeringType.HOLD, TriggeringState.TRIGGERED, akythera$suppressingPriority);
            akythera$fire(manager, TriggeringType.HOLD_UNTIL, TriggeringState.TRIGGERED, akythera$suppressingPriority);
        }

        if (releaseEdge) {
            final boolean holdStarted = Stream.of(TriggeringType.HOLD, TriggeringType.HOLD_UNTIL)
                .map(akythera$boundActions::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(Holder::value)
                .anyMatch(action -> action.priority() >= akythera$suppressingPriority && action.startedHold(akythera$holdTicks));

            if (!holdStarted) {
                akythera$fire(manager, TriggeringType.RELEASE, TriggeringState.TRIGGERED, akythera$highestSuppressingPriority(true));
            }

            akythera$fire(manager, TriggeringType.HOLD, TriggeringState.COMPLETED, akythera$suppressingPriority);
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
            if (action.value().priority() < akythera$suppressingPriority || !action.value().startedHold(lastHold)) continue;

            manager.runEvent(
                action,
                action.value().reachedThreshold(lastHold)
                    ? TriggeringState.COMPLETED
                    : TriggeringState.CANCELED
            );
        }
    }

    /// Runs the bound actions of `triggeringType` at or above `minPriority`. A held action runs once its hold started.
    @Unique
    private void akythera$fire(
        final InputManager manager,
        final TriggeringType triggeringType,
        final TriggeringState triggeringState,
        final int minPriority
    ) {
        List<Holder<InputAction>> bound = akythera$boundActions.get(triggeringType);

        if (bound == null) return;

        for (Holder<InputAction> action : new ArrayList<>(bound)) {
            if (action.value().priority() < minPriority) continue;
            if (triggeringType.isHeld() && !action.value().startedHold(akythera$holdTicks)) continue;

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
