package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.blueprint.particle.ParticleProperties;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/// The state every [AkytheraParticle] keeps apart from vanilla's fields, and the work on it every particle shares:
/// running its emitter's modules and integrating its velocity.
public final class ParticleState {
    private final AkytheraParticleEmitter emitter;

    /// Where the particle spawned, in world space.
    private final Vec3 spawnPoint;

    /// Downward acceleration, in units of 0.04 blocks per tick squared.
    private final float gravityScale;

    /// The share of velocity kept each tick.
    private final float frictionScale;

    /// The emitter's updaters that apply to the particle, run every tick.
    private List<ParticleUpdateModule> updaters = List.of();

    /// In blocks per tick.
    private Vec3 velocity = Vec3.ZERO;

    private RGBA color;

    /// The light the particle gives itself on top of the world's, 0 to 1.
    private float brightness;

    public ParticleState(final AkytheraParticleEmitter emitter, final Vec3 spawnPoint, final ParticleProperties properties, final RGBA color) {
        this.emitter = emitter;
        this.spawnPoint = spawnPoint;
        this.gravityScale = properties.gravity();
        this.frictionScale = properties.friction();
        this.brightness = properties.brightness();
        this.color = color;
    }

    /// Runs the emitter's initializers that apply to `particle`, and keeps the updaters that apply to it.
    public void runInitializers(final AkytheraParticle particle) {
        for (ParticleInitializerModule initializer : emitter.getInitializers()) {
            if (initializer.appliesTo().isInstance(particle)) initializer.initialize(particle);
        }

        updaters = emitter.getUpdaters().stream().filter(updater -> updater.appliesTo().isInstance(particle)).toList();
    }

    /// Applies gravity and friction to the velocity, then runs the updaters on `particle`.
    ///
    /// @return whether `particle` is still alive, which an updater may change
    public boolean update(final AkytheraParticle particle) {
        velocity = velocity.subtract(0.0D, 0.04D * gravityScale, 0.0D).scale(frictionScale);

        for (ParticleUpdateModule updater : updaters) {
            updater.update(particle);

            if (!particle.isAlive()) return false;
        }

        return true;
    }

    /// Takes the motion the particle kept after moving by [#getMotion] and colliding, and slows it on the ground.
    public void moved(final Vec3 keptMotion, final boolean onGround) {
        velocity = onGround ? keptMotion.multiply(0.7D, 1.0D, 0.7D) : keptMotion;
    }

    /// The motion to move by this tick, in blocks.
    public Vec3 getMotion() {
        return velocity;
    }

    /// `worldLight` with the particle's own brightness added.
    public int lightCoords(final int worldLight) {
        return LightCoordsUtil.addSmoothBlockEmission(worldLight, brightness);
    }

    public AkytheraParticleEmitter getEmitter() {
        return emitter;
    }

    public Vec3 getSpawnPoint() {
        return spawnPoint;
    }

    /// In blocks per second.
    public Vec3 getVelocity() {
        return velocity.scale(20.0D);
    }

    /// In blocks per second.
    public void setVelocity(final Vec3 velocity) {
        this.velocity = velocity.scale(1.0D / 20.0D);
    }

    public RGBA getColor() {
        return color;
    }

    public void setColor(final RGBA color) {
        this.color = color;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(final float brightness) {
        this.brightness = brightness;
    }
}
