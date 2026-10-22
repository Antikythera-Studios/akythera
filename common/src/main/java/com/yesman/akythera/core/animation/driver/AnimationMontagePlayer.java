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
import net.minecraft.core.Holder;
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
/// is false. Otherwise, the montage restarts from the beginning, so manual call
/// of [#terminate] will be required.
///
/// The player will start increase [#elapsedTime] and fully deactivated until
/// [#fadeTime] becomes 1 or 0 in each case. This means the fade-in and fade-out
/// times are included for the animation's total time. Both are declared at
/// [AnimationMontage#fadeInTime()] and [AnimationMontage#fadeOutTime()].
///
/// A montage played over another one fades in from the replaced montage, which keeps playing
/// in [#outgoing] until the fade-in ends.
public final class AnimationMontagePlayer implements AnimationDriver {
    /// Cached last pose before call [#terminate]
    @NonNull
    private Pose cachedLastMontagePose = Pose.EMPTY_POSE;

    /// The replaced montage, still playing while the current one fades in. Null when no switch runs.
    private @Nullable AnimationMontagePlayer outgoing;

    /// Blends [#outgoing] into [#playingPose] while [#outgoing] exists.
    private @Nullable PoseBlendDriver crossfade;

    /// The current montage's own pose, without [#crossfade].
    private final AnimationDriver playingPose = new PlayingPose();

    /// Current playing time
    private float elapsedTime;

    /// Last time updated in fixed tick. Memorized for [AnimationNotifyTrack].
    private float lastUpdatedTime;

    /// Last time a frame ran the notifies to. Memorized for [AnimationNotifyTrack#frameUpdate].
    private float lastFrameTime;

    /// The end of the section the playback left since the last tick, which the next tick runs the notifies to first.
    /// NaN while the playback left no section.
    private float leftSectionEnd = Float.NaN;

    /// Where the playback restarted after leaving [#leftSectionEnd]'s section.
    private float leftSectionRestart;

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
        if (currentPlaying != null) {
            interruptNotifies();
            montageCallbackHandle.triggerComplete(animMontage, true);

            // The replaced montage plays on in a copy with its callbacks cut off.
            AnimationMontagePlayer replaced = null;

            if (getWeight() > 0.0F) {
                replaced = new AnimationMontagePlayer();
                replaced.cachedLastMontagePose = cachedLastMontagePose;
                replaced.elapsedTime = elapsedTime;
                replaced.lastUpdatedTime = lastUpdatedTime;
                replaced.lastFrameTime = lastFrameTime;
                replaced.fadeTime = fadeTime;
                replaced.sectionIndex = sectionIndex;
                replaced.isFinished = isFinished;
                replaced.fadingInOrOut = fadingInOrOut;
                replaced.currentPlaying = currentPlaying;
                replaced.montageCallbackHandle = new MontageCallbackHandle(currentPlaying);
                replaced.montageCallbackHandle.invalidate();
                replaced.setOutgoing(outgoing);
            }

            setOutgoing(replaced);
        }

        fadingInOrOut = true;
        isFinished = false;
        elapsedTime = 0.0F;
        lastUpdatedTime = 0.0F;
        lastFrameTime = 0.0F;
        leftSectionEnd = Float.NaN;
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
        float weight = getWeight();
        setOutgoing(null);
        fadeTime = currentPlaying.value().fadeOutTime() * weight;
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
            lastFrameTime = window.from();
            leftSectionEnd = Float.NaN;
        }
    }

    /// Called by fixed tick both in client and server.
    @Override
    public void tick(final ComponentAttachable owner) {
        this.owner = owner;

        if (currentPlaying != null && !Float.isNaN(leftSectionEnd)) {
            currentPlaying.value().tick(owner, lastUpdatedTime, leftSectionEnd);
            lastUpdatedTime = leftSectionRestart;
        }

        leftSectionEnd = Float.NaN;

        if (currentPlaying != null) currentPlaying.value().tick(owner, lastUpdatedTime, elapsedTime);
        lastUpdatedTime = elapsedTime;
    }

    /// Ends the playing montage's notify states the playback sits inside, for a montage that stops before its end.
    private void interruptNotifies() {
        if (owner != null && currentPlaying != null && !isFinished) currentPlaying.value().interrupt(owner, lastUpdatedTime, lastFrameTime);
    }

    private void setOutgoing(@Nullable AnimationMontagePlayer outgoing) {
        this.outgoing = outgoing;
        crossfade = outgoing == null ? null : new PoseBlendDriver(outgoing, playingPose,
            () -> MathUtils.safeDivision(fadeInProgress(), getWeight(), 1.0F));
    }

    /// How far the current montage has faded in, from 0.0 to 1.0.
    private float fadeInProgress() {
        return currentPlaying == null ? 0.0F : MathUtils.safeDivision(fadeTime, currentPlaying.value().fadeInTime(), 1.0F);
    }

    /// Runs the notifies of the time the last frame moved to, whose pose is the one drawn now, then moves the time.
    ///
    /// @param frameTime fixed to 0.05 in a server side, variable frame time in client side.
    @Override
    public void update(final float frameTime) {
        if (outgoing != null) outgoing.update(frameTime);

        // The owner is known from the first tick on. Until then, the frames' notifies wait
        if (owner != null) {
            if (currentPlaying != null) currentPlaying.value().frameUpdate(owner, lastFrameTime, elapsedTime);
            lastFrameTime = elapsedTime;
        }

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
                if (!isFading) setOutgoing(null);
            } else {
                fadeTime = Math.clamp(fadeTime - frameTime, 0.0F, currentPlaying.value().fadeOutTime());
                isFading = fadeTime > 0.0F;

                // Empty the player when the fade time ends
                if (fadeTime <= 0.0F) {
                    elapsedTime = 0.0F;
                    lastUpdatedTime = 0.0F;
                    lastFrameTime = 0.0F;
                    leftSectionEnd = Float.NaN;
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

                float totalTime = currentPlaying.value().getTotalFrameTime();
                boolean reachesEnd = elapsedTime + frameTime > totalTime;

                // Exceeds the section & animation total time bound by increment
                if (currentSectionWindow != null && !currentSectionWindow.isIn(elapsedTime + frameTime) || reachesEnd) {
                    String nextSectionName = sectionInfo.getNextSection(currentSectionName);
                    float sectionEnd = currentSectionWindow == null ? totalTime : Math.min(currentSectionWindow.to(), totalTime);
                    float restartTime;

                    if (nextSectionName != null) {
                        restartTime = sectionInfo.getSectionTimeWindow(nextSectionName).from();
                    } else if (reachesEnd && currentPlaying.value().loops()) {
                        restartTime = 0.0F;
                    } else if (reachesEnd) {
                        // Ends the montage and fades out. The next tick still drives notifies up to the end.
                        elapsedTime = totalTime;
                        fadeOut(null, false);
                        return;
                    } else {
                        // No transition defined → pause at the section end and hold the pose. Stay active
                        // so the caller can jumpToSection (play another sequence) or terminate() explicitly.
                        elapsedTime = sectionEnd;
                        return;
                    }

                    float deltaPlus = elapsedTime + frameTime - sectionEnd;
                    elapsedTime = restartTime + deltaPlus;

                    // The next tick runs the notifies to the section's end, then from the restart
                    if (Float.isNaN(leftSectionEnd)) leftSectionEnd = sectionEnd;
                    leftSectionRestart = restartTime;

                    // The section's end is never drawn, so its notifies run here
                    if (owner != null) currentPlaying.value().frameUpdate(owner, lastFrameTime, sectionEnd);
                    lastFrameTime = restartTime;
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
        // While fading out, returns the raw pose so that it can be blended to the locomotion
        // layer by `getWeight()`
        if (currentPlaying == null || !fadingInOrOut) return cachedLastMontagePose;

        return crossfade == null ? playingPose.getPose() : crossfade.getPose();
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
            float progress = fadeInProgress();

            // Rises from the outgoing montage's weight instead of 0, so the layer under it never shows through.
            return outgoing == null ? progress : outgoing.getWeight() * (1.0F - progress) + progress;
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
            lastFrameTime = montagePlayerSource.lastFrameTime;
            elapsedTime = montagePlayerSource.elapsedTime;
            fadeTime = montagePlayerSource.fadeTime;
            sectionIndex = montagePlayerSource.sectionIndex;
            isFinished = montagePlayerSource.isFinished;
            fadingInOrOut = montagePlayerSource.fadingInOrOut;
        }
    }

    @Override
    public String toString() {
        return currentPlaying + " " + elapsedTime;
    }

    /// Read-only view of the current montage's pose at [#elapsedTime]. Playback control stays on the player.
    private final class PlayingPose implements AnimationDriver {
        @Override
        public Pose getPose() {
            return currentPlaying == null ? cachedLastMontagePose : currentPlaying.value().getPose(elapsedTime);
        }

        @Override
        public @Nullable PlayableAnimation getPlayingAnimation() {
            return AnimationMontagePlayer.this.getPlayingAnimation();
        }

        @Override
        public void update(final float frameTime) {
        }

        @Override
        public float getTime() {
            return elapsedTime;
        }

        @Override
        public void start() {
        }

        @Override
        public void terminate() {
        }

        @Override
        public float getWeight() {
            return fadeInProgress();
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
        }
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
