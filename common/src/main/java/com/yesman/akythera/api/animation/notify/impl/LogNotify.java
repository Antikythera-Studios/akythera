package com.yesman.akythera.api.animation.notify.impl;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;

/// An animation notify that prints out a log in a specified time
///
/// Must be used only for development stage and removed on releases.
public final class LogNotify extends AnimationNotify<EntityPatch<?>> {
    public static final MapCodec<LogNotify> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            LogicalSide.CODEC.optionalFieldOf("side", LogicalSide.BOTH).forGetter(n -> n.logicalSide)
        ).apply(inst, LogNotify::new)
    );

    public LogNotify(LogicalSide side) {
        super(side);
    }

    @Override
    public void onNotified(EntityPatch<?> entityPatch, PlayableAnimation owner, float prevElapsedTime, float currentElapsedTime) {
        Akythera.LOGGER.info("Debug notify: notified at: prev = {}, current = {} in {}", prevElapsedTime, currentElapsedTime, entityPatch.isLogicalClient() ? "client" : "server");
    }

    @Override
    public MapCodec<? extends AnimationNotify<?>> mapCodec() {
        return CODEC;
    }
}