package com.yesman.akythera.core.animation.ik;

import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.util.helper.PoseOperators;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// The [IKChannel] of every IK chain a pose carries: the one [#byTipBone] names for a chain's tip bone, or [#fallback].
public final class IKChannels {
    public static final IKChannels NONE = new IKChannels(IKChannel.NONE, Map.of());
    public static final IKChannels PROCEDURAL = new IKChannels(IKChannel.PROCEDURAL, Map.of());

    /// The channel of a chain whose tip bone [#byTipBone] does not name.
    private final IKChannel fallback;

    private final Map<String, IKChannel> byTipBone;

    public IKChannels(final IKChannel fallback, final Map<String, IKChannel> byTipBone) {
        this.fallback = fallback;
        this.byTipBone = Map.copyOf(byTipBone);
    }

    /// The channel of the chain ending at `tipBone`.
    public IKChannel get(final String tipBone) {
        return byTipBone.getOrDefault(tipBone, fallback);
    }

    /// Between `from` and `to`. A side that carries no channels yields the other, as a bone missing from one pose does
    /// in [PoseOperators#blendPoses].
    public static @Nullable IKChannels lerp(final @Nullable IKChannels from, final @Nullable IKChannels to, final float delta) {
        if (from == null) return to;
        if (to == null || delta <= 0.0F) return from;
        if (delta >= 1.0F) return to;

        Map<String, IKChannel> mixed = new HashMap<>();

        from.byTipBone.keySet().forEach(tipBone -> mixed.put(tipBone, IKChannel.lerp(from.get(tipBone), to.get(tipBone), delta)));
        to.byTipBone.keySet().forEach(tipBone -> mixed.computeIfAbsent(tipBone, _ -> IKChannel.lerp(from.get(tipBone), to.get(tipBone), delta)));

        return new IKChannels(IKChannel.lerp(from.fallback, to.fallback, delta), mixed);
    }

    /// `layered` laid onto `base` the way [PoseOperators#layeredBlendPerBone] lays the bones: a chain mixes by its tip
    /// bone's share. A base that carries no channels counts as [#NONE].
    public static @Nullable IKChannels layered(
        final @Nullable IKChannels base, final @Nullable IKChannels layered, final Armature armature, final float[] boneWeights,
        final float weight
    ) {
        if (layered == null) return base;

        IKChannels under = base == null ? NONE : base;
        Map<String, IKChannel> mixed = new HashMap<>(under.byTipBone);

        armature.visitBones(bone -> {
            float share = Math.min(boneWeights[bone.getId()] * weight, 1.0F);

            if (share > 0.0F) mixed.put(bone.getName(), IKChannel.lerp(under.get(bone.getName()), layered.get(bone.getName()), share));
        });

        return new IKChannels(under.fallback, mixed);
    }
}
