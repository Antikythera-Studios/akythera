package com.yesman.akythera.core.animation.ik;

import com.yesman.akythera.core.animation.keyframe.AnimationClip;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public enum AkytheraIKType implements IKType {
    /// The animation places every chain.
    NONE((_, _) -> IKChannels.NONE),

    /// Procedural stepping places every chain.
    PROCEDURAL((_, _) -> IKChannels.PROCEDURAL),

    /// The clip's [IKCurves] place the chains they name. A chain the clip names no curve for steps procedurally.
    ANIMATION_CURVE((clip, time) -> curveChannels(clip.getIKCurves(), time));

    final BiFunction<AnimationClip, Float, IKChannels> channels;
    final int id;

    AkytheraIKType(final BiFunction<AnimationClip, Float, IKChannels> channels) {
        this.channels = channels;
        id = IKType.ENUM_MANAGER.assign(this);
    }

    @Override
    public IKChannels channels(final AnimationClip clip, final float time) {
        return channels.apply(clip, time);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }

    /// The channels [#ANIMATION_CURVE] gives at `time` under `curves`, by each chain's tip bone. A chain `curves` names
    /// none for steps procedurally.
    public static IKChannels curveChannels(final Map<String, IKCurves> curves, final float time) {
        Map<String, IKChannel> byTipBone = new HashMap<>();

        curves.forEach((tipBone, chainCurves) -> {
            if (!chainCurves.isEmpty()) byTipBone.put(tipBone, chainCurves.channelAt(time));
        });

        return new IKChannels(IKChannel.PROCEDURAL, byTipBone);
    }
}
