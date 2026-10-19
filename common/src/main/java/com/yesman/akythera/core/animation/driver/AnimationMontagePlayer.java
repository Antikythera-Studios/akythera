package com.yesman.akythera.core.animation.driver;

import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.notify.AnimationNotifyTrack;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.animation.playable.SectionInfo;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akythera.core.util.helper.PoseOperators;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// An animation player dedicated to playing [AnimationMontage]
///
/// The player automatically be faded out when the animation elapse time
/// reaches [AnimationMontage#getTotalFrameTime] if [AnimationMontage#loops()]
/// is false. Otherwise, the playback restarts from the beginning until [#terminate]
/// is called.
///
/// The player will start increase [#elapsedTime] and fully deactivated until
/// [#fadeTime] becomes 1 or 0 in each case. This means the fade-in and fade-out
/// times are included for the animation's total time. Both are declared at
/// [AnimationMontage#fadeInTime()] and [AnimationMontage#fadeOutTime()].
public final class AnimationMontagePlayer implements AnimationDriver {
    /// Cached last pose before call [#playAnimation] or [#terminate]
    @NonNull
    private Pose cachedLastMontagePose = Pose.EMPTY_POSE;

    /// [#getWeight] of the montage [#playAnimation] replaced. The fade-in starts from here.
    private float fadeInStartWeight;

    /// Current playing time
    private float elapsedTime;

    /// Last time updated in fixed tick. Memorized for [AnimationNotifyTrack].
    private float lastUpdatedTime;

    /// In the montage player, the fading time is regarded as extended playtime of montage.
    private float fadeTime;

    /// Index of the current playing section
    private int sectionIndex;

    private boolean isFinished = true;

    // true if fading in, false if fading out
    private boolean fadingInOrOut = false;

    /// Montage animation player is reloadable
    private Holder.@Nullable Reference<@NonNull AnimationMontage> currentPlaying;

    /// Current montage callback handler
    private MontageCallbackHandle montageCallbackHandle;

    /// The owner of the last [#tick], which a stopped montage's notify states are ended for.
    private @Nullable ComponentAttachable owner;

    /// Plays an animation montage starting in a specific section
    public MontageCallbackHandle playAnimation(final Holder.@NonNull Reference<@NonNull AnimationMontage> animMontage, @Nullable final String sectionName) {
        fadeInStartWeight = getWeight();

        if (currentPlaying != null) {
            // Cache the current pose of the montage played lastly
            cachedLastMontagePose = getPose();
            interruptNotifies();
            montageCallbackHandle.triggerComplete(animMontage, true);
        }

        fadingInOrOut = true;
        isFinished = false;
        elapsedTime = 0.0F;
        lastUpdatedTime = 0.0F;
        fadeTime = 0.0F;
        currentPlaying = animMontage;
        montageCallbackHandle = new MontageCallbackHandle(animMontage);

        jumpToSection(sectionName);

        return montageCallbackHandle;
    }

    /// For montage players, use [#playAnimation] instead.
    @Override @Deprecated
    public void start() {
        // Do nothing
    }

    @Override
    public void terminate() {
        terminate(null);
    }

    /// Returns the current playing montage section index
    public int getSectionIndex() {
        return sectionIndex;
    }

    /// Do not call this manually, it should only be controlled by [SectionInfo]
    @ApiStatus.Internal
    public void setSectionIndex(final int sectionIndex) {
        this.sectionIndex = sectionIndex;
    }

    /// Starts fading out the montage to stop playing. The montage completely discarded from
    /// player by [AnimationMontage#fadeOutTime()] later.
    ///
    /// @param animMontage give null if you want to terminate any montage
    public void terminate(final Holder.@Nullable Reference<@NonNull AnimationMontage> animMontage) {
        if (currentPlaying == null || animMontage != null && !animMontage.equals(currentPlaying)) {
            // Do nothing when current playing is null or given montage is not matching
            return;
        }

        interruptNotifies();
        fadeOut(animMontage, true);
    }

    /// Completes the montage and fades it out from the current weight.
    private void fadeOut(final Holder.@Nullable Reference<@NonNull AnimationMontage> override, final boolean interrupted) {
        montageCallbackHandle.triggerComplete(override, interrupted);
        cachedLastMontagePose = getPose();
        fadeTime = currentPlaying.value().fadeOutTime() * getWeight();
        fadingInOrOut = false;
        isFinished = true;
    }

    /// Jump the playback time to the section beginning time without triggering any playback
    /// listeners like animation notifies.
    private void jumpToSection(String sectionName) {
        if (StringUtil.isNullOrEmpty(sectionName)) {
            return;
        }

        if (currentPlaying == null) {
            return;
        }

        SectionInfo info = currentPlaying.value().sectionInfo();
        TimeWindow window = info.getSectionTimeWindow(sectionName);

        // null e.g. for the GENERIC default section → leave playhead at 0 as before
        if (window != null) {
            elapsedTime = window.from();
            lastUpdatedTime = window.from();
        }
    }

    /// Called by fixed tick both in client and server.
    @Override
    public void tick(final ComponentAttachable owner) {
        this.owner = owner;

        if (currentPlaying != null) currentPlaying.value().tick(owner, lastUpdatedTime, elapsedTime);
        lastUpdatedTime = elapsedTime;
    }

    /// Ends the playing montage's notify states the last tick sits inside, for a montage that stops before its end.
    private void interruptNotifies() {
        if (owner != null && currentPlaying != null && !isFinished) currentPlaying.value().interrupt(owner, lastUpdatedTime);
    }

    /// @param frameTime fixed to 0.05 in a server side, variable frame time in client side.
    @Override
    public void update(final float frameTime) {
        // Reset to 0 when no there's no playing animation
        if (currentPlaying == null) {
            elapsedTime = 0.0F;
        } else {
            boolean isFading;

            if (fadingInOrOut) {
                float fadeInTime = currentPlaying.value().fadeInTime();
                boolean wasFadingIn = fadeTime < fadeInTime;
                fadeTime = Math.clamp(fadeTime + frameTime, 0.0F, fadeInTime);
                isFading = fadeTime < fadeInTime;

                // fading-in has just completed.
                if (wasFadingIn && !isFading) montageCallbackHandle.triggerBegin();
            } else {
                fadeTime = Math.clamp(fadeTime - frameTime, 0.0F, currentPlaying.value().fadeOutTime());
                isFading = fadeTime > 0.0F;

                // Empty the player when the fade time ends
                if (fadeTime <= 0.0F) {
                    elapsedTime = 0.0F;
                    lastUpdatedTime = 0.0F;
                    currentPlaying = null;
                    cachedLastMontagePose = Pose.EMPTY_POSE;
                    return;
                }
            }

            // Update elapse time if not in the fading stage
            if (!isFading) {
                SectionInfo sectionInfo = currentPlaying.value().sectionInfo();
                String currentSectionName = sectionInfo.getSectionAt(elapsedTime);
                TimeWindow currentSectionWindow = sectionInfo.getSectionTimeWindow(currentSectionName);

                // Exceeds the section & animation total time bound by increment
                if (currentSectionWindow != null && !currentSectionWindow.isIn(elapsedTime + frameTime) || elapsedTime + frameTime > currentPlaying.value().getTotalFrameTime()) {
                    String nextSectionName = sectionInfo.getNextSection(currentSectionName);
                    float totalTime = currentPlaying.value().getTotalFrameTime();

                    if (nextSectionName == null && currentSectionWindow.to() >= totalTime) {
                        if (currentPlaying.value().loops()) {
                            lastUpdatedTime = elapsedTime - totalTime;
                            elapsedTime = elapsedTime + frameTime - totalTime;
                        } else {
                            elapsedTime = totalTime;
                            fadeOut(null, false);
                        }
                    } else if (nextSectionName == null) {
                        // No transition defined → pause at the section end and hold the pose. Stay active
                        // so the caller can jumpToSection (play another sequence) or terminate() explicitly.
                        elapsedTime = currentSectionWindow.to();
                        lastUpdatedTime = elapsedTime; // so notifies between here and the section end don't re-fire each tick
                    } else {
                        // Set elapsed times to the next section
                        float deltaMinus = currentSectionWindow.to() - elapsedTime;
                        float deltaPlus = elapsedTime + frameTime - currentSectionWindow.to();
                        TimeWindow nextSectionTime = sectionInfo.getSectionTimeWindow(nextSectionName);
                        lastUpdatedTime = nextSectionTime.from() - deltaMinus;
                        elapsedTime = nextSectionTime.from() + deltaPlus;
                    }
                } else {
                    elapsedTime += frameTime;
                }
            }
        }
    }

    public Holder.@Nullable Reference<@NonNull AnimationMontage> getPlayingMontage() {
        return currentPlaying;
    }

    /// Returns the notify state of `type` whose time window holds the playhead. Null once the montage
    /// started fading out.
    public <T extends AnimationNotifyState<?>> @Nullable T getActiveNotifyState(final Class<T> type) {
        return currentPlaying == null || isFinished ? null : currentPlaying.value().notifyTrack().getNotifyStateAt(type, elapsedTime);
    }

    @Override
    public Pose getPose() {
        if (currentPlaying == null) return cachedLastMontagePose;

        if (fadingInOrOut) {
            // When fading mode is set to "fade-in", and the time haven't reached the total
            // fade in time, blend the cached last pose to animation pose to start
            float fadeInTime = currentPlaying.value().fadeInTime();

            if (fadeTime < fadeInTime) {
                float share = MathUtils.safeDivision(fadeTime / fadeInTime, getWeight(), 1.0F);
                return PoseOperators.blendPoses(cachedLastMontagePose, 1.0F - share, currentPlaying.value().getPose(elapsedTime), share);
            }
        } else {
            // When fading mode is set to "fade-out", and the time hasn't reached the total
            // fade out time, returns the raw pose so that it can be blended to the locomotion
            // layer by `getWeight()`
            return cachedLastMontagePose;
        }

        return currentPlaying.value().getPose(elapsedTime);
    }

    @Override
    public @Nullable PlayableAnimation getPlayingAnimation() {
        return currentPlaying == null ? null : currentPlaying.value();
    }

    @Override
    public float getTime() {
        return elapsedTime;
    }

    @Override
    public float getWeight() {
        if (currentPlaying == null) {
            return 0.0F;
        }

        if (fadingInOrOut) {
            return Mth.lerp(MathUtils.safeDivision(fadeTime, currentPlaying.value().fadeInTime(), 1.0F), fadeInStartWeight, 1.0F);
        } else {
            return MathUtils.safeDivision(fadeTime, currentPlaying.value().fadeOutTime(), 0.0F);
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public boolean fadingInOrOut() {
        return fadingInOrOut;
    }

    @Override
    public float getFadeTime() {
        return fadeTime;
    }

    @Override
    public void syncDriverTo(@NonNull AnimationDriver source) {
        // Montage player only synced to the driver that runs the same animation montage.
        if (source instanceof AnimationMontagePlayer montagePlayerSource
            && currentPlaying == montagePlayerSource.currentPlaying)
        {
            lastUpdatedTime = montagePlayerSource.lastUpdatedTime;
            elapsedTime = montagePlayerSource.elapsedTime;
            fadeTime = montagePlayerSource.fadeTime;
            fadeInStartWeight = montagePlayerSource.fadeInStartWeight;
            sectionIndex = montagePlayerSource.sectionIndex;
            isFinished = montagePlayerSource.isFinished;
            fadingInOrOut = montagePlayerSource.fadingInOrOut;
        }
    }

    @Override
    public String toString() {
        return currentPlaying + " " + elapsedTime;
    }

    /// A disposable montage playback callback handler
    public static final class MontageCallbackHandle {
        private final Holder<AnimationMontage> targetMontage;
        private final List<MontageBeginCallback> onMontageBegin = new ArrayList<>();
        private final List<MontageCompleteCallback> onMontageCompleted = new ArrayList<>();

        private boolean invalidated;

        MontageCallbackHandle(final Holder<AnimationMontage> targetMontage) {
            this.targetMontage = targetMontage;
        }

        /// Note: The callback won't be triggered if the montage is terminated before fade-in time completely elapses.
        public void addMontageBeginCallback(final MontageBeginCallback task) {
            if (invalidated) Akythera.LOGGER.warn("Tried to add montage begin callback at invalidated handle, Skipped.");
            else onMontageBegin.add(task);
        }

        /// Note: Completion is ensured, called only once per montage play.
        public void addMontageCompletedCallback(final MontageCompleteCallback task) {
            if (invalidated) Akythera.LOGGER.warn("Tried to add montage complete callback at invalidated handle, Skipped.");
            else onMontageCompleted.add(task);
        }

        public void triggerBegin() {
            if (!invalidated) onMontageBegin.forEach(callback -> callback.onBegin(targetMontage));
        }

        public void triggerComplete(@Nullable Holder<AnimationMontage> override, boolean interrupted) {
            if (!invalidated) onMontageCompleted.forEach(callback -> callback.onCompleted(targetMontage, override, interrupted));
            invalidate();
        }

        public void invalidate() {
            invalidated = true;
        }

        public boolean isInvalidated() {
            return invalidated;
        }
    }

    @FunctionalInterface
    public interface MontageBeginCallback {
        /// Called on the montage starts to play, after fading in time.
        void onBegin(Holder<AnimationMontage> animMontage);
    }

    @FunctionalInterface
    public interface MontageCompleteCallback {
        /// Called on the montage being completed both by playback time expiration
        ///
        /// @param override     montage that force finished the current playing montage.
        /// @param interrupted  whether the montage is force finished while playback. `override` still can be null if
        ///                     the montage is finished by [#terminate(Holder.Reference)].
        void onCompleted(Holder<AnimationMontage> animMontage, @Nullable Holder<AnimationMontage> override, boolean interrupted);
    }
}
