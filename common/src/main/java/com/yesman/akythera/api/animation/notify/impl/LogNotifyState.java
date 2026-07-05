package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;

/// An animation notify that prints out a log in a specified time window
///
/// Must be used only for development stage and removed on releases.
public final class LogNotifyState extends AnimationNotifyState<EntityPatch<?>> {
    public static final MapCodec<LogNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            LogicalSide.CODEC.optionalFieldOf("side", LogicalSide.BOTH).forGetter(n -> n.logicalSide)
        ).apply(inst, LogNotifyState::new)
    );

    public LogNotifyState(LogicalSide side) {
        super(side);
    }

    @Override
    public void onBegin(EntityPatch<?> entityPatch, PlayableAnimation owner) {
        Akythera.LOGGER.info("Notified {}, begin notified in {}", owner, entityPatch.isLogicalClient() ? "client" : "server");
    }

    @Override
    public void onTick(EntityPatch<?> entityPatch, PlayableAnimation owner, float prevElapsedTime, float currentElapsedTime) {
        Akythera.LOGGER.info("Notified {}, tick notified at: prev = {}, current = {} in {}", owner, prevElapsedTime, currentElapsedTime, entityPatch.isLogicalClient() ? "client" : "server");
    }

    @Override
    public void onEnd(EntityPatch<?> entityPatch, PlayableAnimation owner) {
        Akythera.LOGGER.info("Notified {}, end notified in {}", owner, entityPatch.isLogicalClient() ? "client" : "server");
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }
}