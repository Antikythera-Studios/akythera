package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;

/// An animation notify that prints out a log in a specified time window
///
/// Must be used only for development stage and removed on releases.
public final class LogNotifyState extends AnimationNotifyState<ComponentAttachable> {
    public static final MapCodec<LogNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            LogicalSide.CODEC.optionalFieldOf("side", LogicalSide.BOTH).forGetter(n -> n.logicalSide)
        ).apply(inst, LogNotifyState::new)
    );

    public LogNotifyState(LogicalSide side) {
        super(side);
    }

    @Override
    public void onBegin(ComponentAttachable owner, PlayableAnimation animation, TimeWindow timeWindow) {
        Akythera.LOGGER.info("Notified {}, begin notified in {}", animation, owner.logicalSide().getSerializedName());
    }

    @Override
    public void onTick(ComponentAttachable owner, PlayableAnimation animation, float prevElapsedTime, float currentElapsedTime) {
        Akythera.LOGGER.info("Notified {}, tick notified at: prev = {}, current = {} in {}", animation, prevElapsedTime, currentElapsedTime, owner.logicalSide().getSerializedName());
    }

    @Override
    public void onEnd(ComponentAttachable owner, PlayableAnimation animation, boolean interrupted) {
        Akythera.LOGGER.info("Notified {}, end notified in {}", animation, owner.logicalSide().getSerializedName());
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}