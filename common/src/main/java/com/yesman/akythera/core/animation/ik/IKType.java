package com.yesman.akythera.core.animation.ik;

import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;

/// How an animation places the IK chains while it plays, declared by animation sequences and montages.
public interface IKType extends EnumerableDataBlock {
    EnumerableDataBlockManager<IKType> ENUM_MANAGER = new EnumerableDataBlockManager<>("ik_type");

    /// The channels the chains take while `clip` plays at `time`.
    IKChannels channels(AnimationClip clip, float time);

    @Override
    default EnumerableDataBlockManager<? extends EnumerableDataBlock> getEnumManager() {
        return ENUM_MANAGER;
    }
}
