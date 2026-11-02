package com.yesman.akythera.api.animation.notify.impl;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.api.animation.notify.NotifyTriggerThread;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.lifecycle.ClientComponentsFactory;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.client.api.particle.AkytheraParticles;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/// Plays a particle system that follows a socket of the entity, over the notify state's time window. Checked every
/// frame: the frame that passes the window's start starts the system back at it, and every emitter with a time span,
/// a trail or spawn rate one, takes the window's length in real time as its time span
/// ([ParticleEmitterBlueprint#withTimeSpan]). Each frame then gives the system a [#frameSampler].
///
/// Real time comes from how far the frame moved the animation, so it follows whatever drives the animation's speed.
public final class TrailParticleNotifyState extends AnimationNotifyState<EntityPatch<?>> {
    public static final MapCodec<TrailParticleNotifyState> CODEC = RecordCodecBuilder.mapCodec(inst ->
        inst.group(
            ResourceKey.codec(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY).fieldOf("particle_system").forGetter(TrailParticleNotifyState::particleSystem),
            Codec.STRING.optionalFieldOf("socket", "").forGetter(TrailParticleNotifyState::socket)
        ).apply(inst, TrailParticleNotifyState::new)
    );

    private final ResourceKey<ParticleSystemBlueprint> particleSystem;

    /// The [ParticleAttachment#socketPath] the system follows.
    private final String socket;

    public TrailParticleNotifyState(final ResourceKey<ParticleSystemBlueprint> particleSystem, final String socket) {
        super(LogicalSide.CLIENT, NotifyTriggerThread.RENDER);

        this.particleSystem = particleSystem;
        this.socket = socket;
    }

    @Override
    public void onBegin(final EntityPatch<?> owner, final PlayableAnimation animation, final TimeWindow timeWindow) {
        ParticleSystemBlueprint blueprint = owner.getOriginal().level().registryAccess()
            .lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY)
            .getValue(particleSystem);

        if (blueprint == null) {
            Akythera.LOGGER.warn("Trail particle notify state names the unknown particle system {}", particleSystem.identifier());
            return;
        }

        ClientComponentsFactory.getImpl().getTrailParticlePlays(owner).put(this, new Play(blueprint, timeWindow, new ParticleAttachment(owner.getOriginal().getId(), socket)));
    }

    /// Starts the system on the first frame, then gives it the frame's [#frameSampler].
    @Override
    public void onTick(final EntityPatch<?> owner, final PlayableAnimation animation, final float prevElapsedTime, final float currentElapsedTime) {
        Map<TrailParticleNotifyState, Play> plays = ClientComponentsFactory.getImpl().getTrailParticlePlays(owner);
        Play play = plays.get(this);
        float frameTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();

        if (play == null || currentElapsedTime <= prevElapsedTime || frameTicks <= 0.0F) return;

        float ticksPerSecond = frameTicks / (currentElapsedTime - prevElapsedTime);
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Level level = owner.getOriginal().level();

        if (play.system == null) {
            Transform transform = play.attachment.worldTransform(level, partialTick);
            float span = (play.window.to() - play.window.from()) * ticksPerSecond / 20.0F;
            float behind = (currentElapsedTime - play.window.from()) * ticksPerSecond;
            ParticleSystemBlueprint blueprint = new ParticleSystemBlueprint(
                play.blueprint.emitters().stream().map(emitter -> emitter.withTimeSpan(span)).toList(),
                play.blueprint.variables()
            );

            play.system = transform == null ? null : AkytheraParticles.play(blueprint, transform, play.attachment, partialTick - behind);

            if (play.system == null) {
                plays.remove(this);
                return;
            }
        }

        ArmatureComponent armature = owner.getArmatureComponent();

        play.system.setFrameSampler(armature == null ? null : frameSampler(
            armature,
            animation::getPose,
            play.window,
            prevElapsedTime,
            currentElapsedTime,
            play.system.getAge() + partialTick,
            ticksPerSecond,
            () -> play.attachment.worldTransform(level, partialTick)
        ));
    }

    /// The system plays on to the end of its time span unless the animation was interrupted.
    @Override
    public void onEnd(final EntityPatch<?> owner, final PlayableAnimation animation, final boolean interrupted) {
        Play play = ClientComponentsFactory.getImpl().getTrailParticlePlays(owner).remove(this);

        if (interrupted && play != null && play.system != null) play.system.stop();
    }

    /// Where `socket` was while the animation went from `prev` to `cur` inside `window`, for the frame drawing the
    /// animation at `cur`. A sampled pose is laid over the armature's drawn pose by how the animation moves from `cur`
    /// ([Pose#offsetBy]), and the socket is read with the armature bound to it ([ArmatureComponent#readAt]). The entity
    /// stays where the frame draws it, so a sample carries the pose's motion alone.
    ///
    /// @param poseAt         the animation's pose at a time
    /// @param frameTime      the frame's time on the system's clock, see [AkytheraParticleSystem#getAge]
    /// @param ticksPerSecond real ticks per second of the animation over the frame
    /// @param socket         the socket's world transform with its entity placed as the frame draws it, null once it is
    ///                       gone
    public static AkytheraParticleSystem.FrameSampler frameSampler(
        final ArmatureComponent armature,
        final Function<Float, Pose> poseAt,
        final TimeWindow window,
        final float prev,
        final float cur,
        final float frameTime,
        final float ticksPerSecond,
        final Supplier<@Nullable Transform> socket
    ) {
        float from = Math.max(prev, window.from());
        float to = Math.min(cur, window.to());
        Supplier<Pose> drawnAnimationPose = Suppliers.memoize(() -> poseAt.apply(cur));

        return new AkytheraParticleSystem.FrameSampler(frameTime - (cur - from) * ticksPerSecond, frameTime - (cur - to) * ticksPerSecond, time -> {
            Pose drawn = armature.getCachedPose();

            if (drawn == null) return null;

            float animationTime = Mth.clamp(cur - (frameTime - time) / ticksPerSecond, from, to);

            return armature.readAt(drawn.offsetBy(drawnAnimationPose.get(), poseAt.apply(animationTime)), socket);
        });
    }

    public ResourceKey<ParticleSystemBlueprint> particleSystem() {
        return particleSystem;
    }

    public String socket() {
        return socket;
    }

    @Override
    public MapCodec<? extends AnimationNotifyState<?>> mapCodec() {
        return CODEC;
    }

    /// One owner's play, from [#onBegin] to [#onEnd]: the system its first [#onTick] starts from `blueprint`. Its
    /// system is stopped when the animation is interrupted.
    public static final class Play {
        private final ParticleSystemBlueprint blueprint;
        private final TimeWindow window;
        private final ParticleAttachment attachment;
        private @Nullable AkytheraParticleSystem system;

        private Play(final ParticleSystemBlueprint blueprint, final TimeWindow window, final ParticleAttachment attachment) {
            this.blueprint = blueprint;
            this.window = window;
            this.attachment = attachment;
        }
    }
}
