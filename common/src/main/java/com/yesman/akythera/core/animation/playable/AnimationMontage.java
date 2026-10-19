package com.yesman.akythera.core.animation.playable;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.animation.AnimationManager;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.animation.AnimationLayerSignature;
import com.yesman.akythera.core.animation.driver.AnimationMontagePlayer;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationLayerSignature;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.animation.keyframe.BoneAnimationTrack;
import com.yesman.akythera.core.animation.keyframe.Keyframe;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Supplier;

/// A playable animation with complex tasks and events based on the playback time.
/// Run by [AnimationMontagePlayer] to modify states of the owner.
///
/// Supports client-server synchronization based on data pack system.
///
/// If you don't need such complex animation system, use [AnimationSequence] with
/// only necessary information required to be run by drivers.
///
/// Inspired by Unreal Engine 5's
/// [Animation Montage](https://dev.epicgames.com/documentation/unreal-engine/animation-montage-in-unreal-engine)
/// system. Most terminologies are from there.
@ScriptType(autoRegister = false)
public final class AnimationMontage implements PlayableAnimation {
    public static final MapCodec<AnimationMontage> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AnimationNotifyTrack.CODEC.optionalFieldOf("notify_track", AnimationNotifyTrack.EMPTY).forGetter(AnimationMontage::notifyTrack),
                SectionInfo.CODEC.fieldOf("sections").forGetter(AnimationMontage::sectionInfo),
                AnimationManager.CODEC.listOf().fieldOf("animations").forGetter(AnimationMontage::getAnimSegments),
                AnimationLayerSignature.ENUM_MANAGER.getNameCodec().fieldOf("layer_signature").forGetter(AnimationMontage::layerSignature),
                Codec.FLOAT.fieldOf("fade_in_time").forGetter(AnimationMontage::fadeInTime),
                Codec.FLOAT.fieldOf("fade_out_time").forGetter(AnimationMontage::fadeOutTime),
                Codec.BOOL.fieldOf("loops").forGetter(AnimationMontage::loops),
                Codec.BOOL.fieldOf("root_motion").forGetter(AnimationMontage::rootMotion)
            )
            .apply(instance, AnimationMontage::new)
    );

    public static final Codec<AnimationMontage> CODEC = MAP_CODEC.codec();

    /// Generic montage section name that covers the whole timeline
    public static final String GENERIC_SECTION_NAME = "generic";

    /// Tiny time gap inserted between merged segments so adjacent segments' boundary keyframes don't
    /// collide on a single timestamp. Code that jumps *into* a section must clear this gap so it lands
    /// on the new segment's first frame, not the previous segment's last (see the editor montage animator).
    public static final float SEGMENT_GAP = 0.0001F;

    /// Tolerance (~1 frame @60fps) for deciding a segment's final keyframe already sits at its end, so
    /// the captured last pose isn't duplicated.
    public static final float LAST_FRAME_EPSILON = 0.01666F;

    /// Notify timeline. Montages accept all logical sides since they are synced
    /// across client/server via the data-pack registry.
    private final AnimationNotifyTrack notifyTrack;

    /// Animation playback control by sections & playing order
    private final SectionInfo sectionInfo;

    /// Merged montage animation. Won't be reloaded when animation changes.
    ///
    /// Lazy build to keep the [AssetAccessor] principle
    private final Supplier<AnimationClip> mergedClip;

    /// Maintain for serialization
    private final List<AssetAccessor<AnimationClip>> segments;

    /// Animation layer signature where the montage should play
    private final AnimationLayerSignature layerSignature;

    /// Whether the montage resets playback time when animation is done
    ///
    /// NOTE: the montage can also loop by [#sectionInfo]. This field only resets
    /// the playback time when it hits the max frame time, and you can't use this
    /// field to judge whether the montage actually loops or not.
    private final boolean loops;

    /// Whether **Root Motion** is enabled
    ///
    /// If enabled, playing the montage will sync the entity's location to the bone named `root`
    /// which is common bone name that is on the top of armature hierarchy
    ///
    /// @see Armature#getRootBone
    private final boolean rootMotion;

    /// Pose blending time when montage starts to play
    private final float fadeInTime;

    /// Pose blending time when montage terminates
    private final float fadeOutTime;

    private AnimationMontage(
        final AnimationNotifyTrack notifyTrack,
        final SectionInfo sectionInfo,
        final List<AssetAccessor<AnimationClip>> animationSegments,
        final AnimationLayerSignature layerSignature,
        final float fadeInTime,
        final float fadeOutTime,
        final boolean loops,
        final boolean rootMotion
    ) {
        this.notifyTrack = notifyTrack;
        this.sectionInfo = sectionInfo;
        this.fadeInTime = fadeInTime;
        this.fadeOutTime = fadeOutTime;
        this.segments = animationSegments;
        this.layerSignature = layerSignature;
        this.loops = loops;
        this.rootMotion = rootMotion;
        this.mergedClip = Suppliers.memoize(this::buildMergedClip);
    }

    private AnimationClip buildMergedClip() {
        AnimationClip.Builder animClipBuilder = AnimationClip.builder();
        float time = 0.0F;
        // Running root offset so each segment's root track continues where the previous ended.
        // Applied only to the root bone, only while root motion is on (otherwise an in-place montage
        // would drift across segments). Without this, root motion lurches backward at every segment
        // join (RootMotionComponent sees the per-segment reset as a huge reverse delta).
        Vec3 rootAccum = Vec3.ZERO;
        Map<String, List<Keyframe>> bones = new HashMap<>();

        // Union of every bone across all segments. A bone missing from a segment is pinned to identity
        // across that segment's window so a neighbour's keyframes can't bleed into it
        Set<String> allBones = new HashSet<>();

        for (AssetAccessor<AnimationClip> animation : segments) {
            animation.get().visitBoneTracks((name, _) -> allBones.add(name));
        }

        for (AssetAccessor<AnimationClip> animation : segments) {
            final AnimationClip clip = animation.get();
            final float segmentOffset = time;
            final float segEnd = clip.getTotalTime();

            final Vec3 segRootStart = rootMotion ? rootLocOf(clip, 0.0F) : Vec3.ZERO;
            final Vec3 rootShift = rootMotion ? rootAccum.subtract(segRootStart) : Vec3.ZERO;

            final Pose firstPose = clip.getPose(0.0F);
            final Pose lastPose = clip.getPose(segEnd);

            for (String name : allBones) {
                List<Keyframe> merged = bones.computeIfAbsent(name, k -> new ArrayList<>());

                if (!clip.hasBoneTransform(name)) {
                    // Not animated in this segment → hold identity across its window.
                    merged.add(new Keyframe(segmentOffset, Transform.IDENTITY));
                    merged.add(new Keyframe(segEnd + segmentOffset, Transform.IDENTITY));
                    continue;
                }

                // First pose
                merged.add(new Keyframe(segmentOffset, shiftRoot(name, firstPose.orElseIdentity(name), rootShift)));

                // Own track keyframes
                for (Keyframe kf : clip.getBoneTransform(name).keyframes()) {
                    merged.add(new Keyframe(kf.timeStamp() + segmentOffset, shiftRoot(name, kf.transform(), rootShift)));
                }

                // Last pose, unless the final keyframe already sits at the segment end
                if (Math.abs(merged.getLast().timeStamp() - (segEnd + segmentOffset)) > LAST_FRAME_EPSILON) {
                    merged.add(new Keyframe(segEnd + segmentOffset, shiftRoot(name, lastPose.orElseIdentity(name), rootShift)));
                }
            }

            if (rootMotion) {
                rootAccum = rootAccum.add(rootLocOf(clip, segEnd).subtract(segRootStart));
            }

            time += segEnd + SEGMENT_GAP;
        }

        bones.forEach((name, keyframes) ->
            animClipBuilder.addBoneAnimation(name, new BoneAnimationTrack(keyframes))
        );

        return animClipBuilder.build();
    }

    /// Raw root-bone location at `t` (clip pose keeps the root, unlike [#getPose]).
    private static Vec3 rootLocOf(AnimationClip clip, float t) {
        return clip.getPose(Math.clamp(t, 0.0F, clip.getTotalTime()))
            .orElseZero(ArmatureLoader.ROOT_BONE).loc();
    }

    /// Returns `transform` with `rootShift` added to its location — but only for the root bone.
    private static Transform shiftRoot(String boneName, Transform transform, Vec3 rootShift) {
        if (!ArmatureLoader.ROOT_BONE.equals(boneName)) return transform;
        return new Transform(transform.loc().add(rootShift), transform.rot(), transform.sca());
    }

    public List<AssetAccessor<AnimationClip>> getAnimSegments() {
        return segments;
    }

    public AnimationClip getMergedAnimClip() {
        return mergedClip.get();
    }

    public AnimationLayerSignature layerSignature() {
        return layerSignature;
    }

    public boolean rootMotion() {
        return rootMotion;
    }

    @Override
    public float fadeInTime() {
        return fadeInTime;
    }

    @Override
    public float fadeOutTime() {
        return fadeOutTime;
    }

    @Override
    public void tick(final ComponentAttachable owner, final float prevElapsedTime, final float currentElapsedTime) {
        notifyTrack.tick(this, owner, prevElapsedTime, currentElapsedTime);
    }

    @Override
    public void interrupt(final ComponentAttachable owner, final float elapsedTime) {
        notifyTrack.interrupt(this, owner, elapsedTime);
    }

    @Override
    public Pose getPose(final float time) {
        Pose pose = mergedClip.get().getPose(time);

        // Clear the root transform while `rootMotion` is activated
        if (rootMotion() && pose.hasTransform(ArmatureLoader.ROOT_BONE)) {
            pose.putBoneTransform(ArmatureLoader.ROOT_BONE, Transform.IDENTITY);
        }

        return pose;
    }

    @Override
    public float getTotalFrameTime() {
        return mergedClip.get().getTotalTime();
    }

    @Override
    public Transform getRootMotionTransform(final float time) {
        AnimationClip clip = mergedClip.get();
        return clip.getPose(Math.clamp(time, 0.0F, clip.getTotalTime()))
            .orElse(ArmatureLoader.ROOT_BONE, Transform.IDENTITY);
    }

    @Override
    public boolean loops() {
        return loops;
    }

    public AnimationNotifyTrack notifyTrack() {
        return notifyTrack;
    }

    public SectionInfo sectionInfo() {
        return sectionInfo;
    }

    @Override
    public @NonNull String toString() {
        return StaticRegistryAccess.getRegisteredName(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, this);
    }

    public static Builder builder() {
        return new AnimationMontage.Builder();
    }

    public static class Builder {
        final List<AnimationNotifyTrack.NotifyInTimeStamp> notifies = new ArrayList<>();
        final List<AnimationNotifyTrack.NotifyStateInTimeWindow> notifyStates = new ArrayList<>();
        final Map<String, Float> sections = new HashMap<>();
        final List<List<String>> sectionPlayingOrders = new ArrayList<>();
        List<AssetAccessor<AnimationClip>> animSegments = new ArrayList<>();
        AnimationLayerSignature layerSignature = AkytheraAnimationLayerSignature.BASE;
        float fadeInTime = 0.2F;
        float fadeOutTime = 0.2F;
        boolean loops;
        boolean enableRootMotion;

        private Builder() {}

        /// Adds anim notify in a given timestamp
        public Builder addAnimationNotify(final AnimationNotify<?> notify, final float timeStamp) {
            notifies.add(new AnimationNotifyTrack.NotifyInTimeStamp(notify, timeStamp));
            return this;
        }

        /// Adds anim notify state in given time window. Ensures calling [AnimationNotifyState#onTick] and [AnimationNotifyState#onEnd]
        /// at least once if [AnimationNotifyState#onBegin] is called.
        public Builder addAnimationNotifyState(final AnimationNotifyState<?> notifyState, final float timeStampStart, final float timeStampEnd) {
            notifyStates.add(new AnimationNotifyTrack.NotifyStateInTimeWindow(notifyState, TimeWindow.create(timeStampStart, timeStampEnd)));
            return this;
        }

        /// Split montage track in a given timestamp into sections. Sections are used on controlling animation playback.
        public Builder createMontageSectionAt(final String sectionName, final float time) {
            sections.put(sectionName, time);
            return this;
        }

        /// Adds one section play sequence. Call again to add more sequences in the same montage.
        /// The playback time switches to the next section when the current one ends.
        ///
        /// Suppose these three montage sections,
        ///
        /// > `Begin`(0.0 ~ 0.5), `Charge`(0.5 ~ 2.0), `Fire`(2.0 ~ 2.25)
        ///
        /// and a sequence,
        ///
        /// > `Begin` -> `Charge` -> `Charge`
        ///
        /// The montage started from `Begin` will loop in charge section (`Charge`->`Charge`).
        ///
        /// Core rule: **one transition per section.** A section may appear in many sequences, but it must
        /// always lead to the same next section. Defining both `A`->`B` and `A`->`C` is ambiguous and throws
        /// at build. A section with no defined next pauses (holds its end pose) until terminated or jumped.
        ///
        /// In the example above, if you want to play fire animations, you should call
        /// [Animator#playAnimMontage(ResourceKey, String)] once again with `Fire` section name.
        ///
        /// WARNING: if given section names are not included in section timestamps specified by `createMontageSectionAt`,
        /// it will cause an exception in build phase.
        public Builder addSectionPlayingOrder(final String... sectionNames) {
            sectionPlayingOrders.add(List.of(sectionNames));
            return this;
        }

        /// Sets animation source
        public Builder addAnimSegment(final AssetAccessor<AnimationClip> animClip) {
            animSegments.add(animClip);
            return this;
        }

        public Builder layerSignature(final AnimationLayerSignature layerSignature) {
            this.layerSignature = layerSignature;
            return this;
        }

        /// Fade in time extends animation frame time in front of the transform track unlike Unreal Engine works
        public Builder fadeInTime(final float fadeInTime) {
            this.fadeInTime = fadeInTime;
            return this;
        }

        /// Fade out time extends animation frame time behind the transform track unlike Unreal Engine works
        ///
        /// Commonly set to 0.0 and let next animation handle the blending time. This is for animations that
        /// great pose transition is expected. (like heavy weapon animations)
        public Builder fadeOutTime(final float fadeOutTime) {
            this.fadeOutTime = fadeOutTime;
            return this;
        }

        /// Determines whether the montage should be reset when hits the max animation time. Applied after
        /// montage section looping is applied.
        public Builder loops(final boolean loops) {
            this.loops = loops;
            return this;
        }

        /// Determines whether the montage activate root motion
        ///
        /// If enabled, playing the montage will sync the player movement to the bone named `root`
        public Builder rootMotionEnabled(final boolean enableRootMotion) {
            this.enableRootMotion = enableRootMotion;
            return this;
        }

        public AnimationMontage create() throws IllegalStateException {
            return new AnimationMontage(
                new AnimationNotifyTrack(
                    notifies,
                    notifyStates
                ),
                new SectionInfo(
                    sections,
                    sectionPlayingOrders
                ),
                animSegments,
                layerSignature,
                fadeInTime,
                fadeOutTime,
                loops,
                enableRootMotion
            );
        }
    }
}
