package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.particle.EmitterSpawnRule;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import com.yesman.akythera.core.blueprint.particle.SpriteParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailSpawnRule;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// One emitter of an [AkytheraParticleSystem], spawning the particle its [ParticleEmitterBlueprint] names by its
/// spawn rule. Has no presence in the particle engine; its system ticks it.
public class AkytheraParticleEmitter {
    private final AkytheraParticleSystem system;
    private final ParticleEmitterBlueprint blueprint;

    /// Null when the blueprint names a particle the system's host lacks, or a trail spawned by a rule other than
    /// [TrailSpawnRule], which finishes the emitter at once.
    private final @Nullable ParticleBlueprint particle;

    /// The live particles this emitter spawned, kept only when [ParticleEmitterBlueprint#hideParticlesOnFinish].
    private final List<AkytheraParticle> spawned = new ArrayList<>();

    /// The playback state of the blueprint's spawn rule.
    private EmitterSpawnRule.Spawner spawner;

    /// The modules [ParticleEmitterBlueprint#initializers] build, without those that failed to build.
    private final List<ParticleInitializerModule> initializers = new ArrayList<>();

    /// The modules [ParticleEmitterBlueprint#updaters] build, without those that failed to build.
    private final List<ParticleUpdateModule> updaters = new ArrayList<>();

    /// The emitter's transform in world space. While a particle is being built, the transform at the moment that
    /// particle spawns.
    private Transform worldTransform;

    /// The emitter's transform in its system's space, the one [#worldTransform] was built from.
    private Transform placement;

    /// Seconds since the emitter started at its last tick, or -1 before its first tick.
    private float lastLocal = -1.0F;

    /// Seconds since the emitter started at the moment the last particle it built spawned.
    private float spawnTime;

    /// The emitter's transform in its system's space. While a particle is being built, the one at the moment that
    /// particle spawns.
    public Transform getPlacement() {
        return placement;
    }

    /// How far the emitter moved over the last tick, in world space.
    private Vec3 tickMotion = Vec3.ZERO;

    private boolean finished;

    public AkytheraParticleEmitter(final AkytheraParticleSystem system, final ParticleEmitterBlueprint blueprint) {
        this.system = system;
        this.blueprint = blueprint;
        ParticleBlueprint named = blueprint.particle().map(
            system.getHost()::piece,
            SpriteParticleBlueprint::ofVanilla
        );

        if (named == null) {
            Akythera.LOGGER.warn("Emitter {} names the unknown particle {}", blueprint.name(), blueprint.particle().left().orElseThrow().identifier());
        } else if (named instanceof TrailParticleBlueprint && !(blueprint.spawn() instanceof TrailSpawnRule)) {
            Akythera.LOGGER.warn("Emitter {} spawns a trail by a spawn rule other than trail", blueprint.name());
            named = null;
        }

        this.particle = named;
        this.finished = named == null;
        this.placement = blueprint.placementAt(0.0F);
        this.worldTransform = system.getTransform().mul(this.placement);
        this.spawner = blueprint.spawn().spawner();

        for (ParticleInitializerBlueprint initializer : blueprint.initializers()) {
            try {
                this.initializers.add(initializer.instantiate(this));
            } catch (IllegalStateException e) {
                Akythera.LOGGER.warn("Emitter {} skips an initializer: {}", blueprint.name(), e.getMessage());
            }
        }

        for (ParticleUpdaterBlueprint updater : blueprint.updaters()) {
            try {
                this.updaters.add(updater.instantiate(this));
            } catch (IllegalStateException e) {
                Akythera.LOGGER.warn("Emitter {} skips an updater: {}", blueprint.name(), e.getMessage());
            }
        }
    }

    /// Spawns what the spawn rule owes at `seconds` since the system started, and finishes the emitter once the rule
    /// is over.
    public void tick(final float seconds) {
        if (finished) return;

        float local = seconds - blueprint.start();

        if (local < 0.0F) return;

        if (spawner.isOver(local)) {
            finish();

            return;
        }

        float previous = lastLocal < 0.0F ? local : lastLocal;
        int count = spawner.tick(local);
        Transform currentPlacement = blueprint.placementAt(blueprint.spawn().spanTime(local));
        Transform current = system.getTransform().mul(currentPlacement);
        tickMotion = lastLocal < 0.0F ? Vec3.ZERO : current.loc().subtract(worldTransform.loc());
        lastLocal = local;

        // The tick's particles spread evenly over the time since the last tick, each at its own transform
        for (int i = 1; i <= count; i++) {
            spawnTime = previous + (local - previous) * i / count;
            placement = blueprint.placementAt(blueprint.spawn().spanTime(spawnTime));
            worldTransform = system.getTransformAt((float)i / count).mul(placement);

            AkytheraParticle spawnedParticle;

            try {
                spawnedParticle = particle.instantiate(this);
            } catch (IllegalStateException e) {
                Akythera.LOGGER.warn("Emitter {} can't build its particle and stops: {}", blueprint.name(), e.getMessage());
                finish();

                return;
            }

            spawnedParticle.emitterInit(this);
            system.getHost().add(spawnedParticle);

            if (blueprint.hideParticlesOnFinish()) spawned.add(spawnedParticle);
        }

        placement = currentPlacement;
        worldTransform = current;
        spawned.removeIf(live -> !live.isAlive());
    }

    /// Moves the emitter to `seconds` since its system started, as if it had played there: a rule with no time span
    /// whose start is behind counts as spawned, and one ahead spawns again when the play reaches it.
    public void seek(final float seconds) {
        if (particle == null) return;

        float local = seconds - blueprint.start();
        spawner = blueprint.spawn().spawner();
        lastLocal = -1.0F;
        finished = false;

        if (local > 0.0F && blueprint.spawn().duration() <= 0.0F || local >= 0.0F && spawner.isOver(local)) finish();
    }

    /// Stops spawning, and removes the live particles this emitter spawned when it hides them on finish.
    public void finish() {
        finished = true;
        spawned.forEach(AkytheraParticle::remove);
        spawned.clear();
    }

    public boolean isFinished() {
        return finished;
    }

    public AkytheraParticleSystem getSystem() {
        return system;
    }

    public ParticleEmitterBlueprint getBlueprint() {
        return blueprint;
    }

    public Transform getWorldTransform() {
        return worldTransform;
    }

    /// Seconds since the emitter started at the moment the last particle it built spawned. While a particle is being
    /// built, the moment that particle spawns.
    public float getSpawnTime() {
        return spawnTime;
    }

    /// How far the emitter moved over the last tick, in world space. A new particle starts this far back as its
    /// previous position, so on its first frames it draws along with whatever the emitter rides, which is drawn
    /// between its last two ticks.
    public Vec3 getTickMotion() {
        return tickMotion;
    }

    public List<ParticleInitializerModule> getInitializers() {
        return initializers;
    }

    public List<ParticleUpdateModule> getUpdaters() {
        return updaters;
    }
}
