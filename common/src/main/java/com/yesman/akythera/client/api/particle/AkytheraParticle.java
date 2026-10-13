package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/// A particle an [AkytheraParticleEmitter] spawns. Every implementation is a vanilla particle that keeps its own
/// motion and look state, and writes vanilla's fields only where vanilla reads them.
@ScriptType
public interface AkytheraParticle {
    /// The state this particle keeps apart from vanilla's fields.
    ParticleState state();

    /// The emitter that spawned this particle.
    default AkytheraParticleEmitter getEmitter() {
        return state().getEmitter();
    }

    /// Where this particle spawned, in world space.
    default Vec3 getSpawnPoint() {
        return state().getSpawnPoint();
    }

    /// Where this particle is now, in world space.
    Vec3 getPosition();

    RandomSource getRandom();

    boolean isAlive();

    void remove();

    /// Runs the emitter's [ParticleInitializerModule]s that apply to this particle ([ParticleState#runInitializers]),
    /// keeps the [ParticleUpdateModule]s that apply to run every tick, and starts the previous position one tick of
    /// emitter motion back ([AkytheraParticleEmitter#getTickMotion]). Called once, right after the particle is built.
    void emitterInit(AkytheraParticleEmitter emitter);

    /// Moves the particle by the given offset, in blocks, colliding with blocks when it has physics.
    void move(double x, double y, double z);

    void setPos(double x, double y, double z);

    @ScriptFunction(category = "Particle", desc = "Places the particle at a world position")
    default void setPosition(final Vec3 position) {
        setPos(position.x, position.y, position.z);
    }

    @ScriptFunction(pure = true, category = "Particle", desc = "The particle's velocity, in blocks per second")
    default Vec3 getVelocity() {
        return state().getVelocity();
    }

    @ScriptFunction(category = "Particle", desc = "Sets the particle's velocity, in blocks per second")
    default void setVelocity(final Vec3 velocity) {
        state().setVelocity(velocity);
    }

    @ScriptFunction(pure = true, category = "Particle")
    default RGBA getColor() {
        return state().getColor();
    }

    @ScriptFunction(category = "Particle")
    default void setColor(final RGBA color) {
        state().setColor(color);
    }

    /// The light the particle gives itself on top of the world's, 0 to 1.
    @ScriptFunction(pure = true, category = "Particle", desc = "The light the particle gives itself, 0 to 1")
    default float getBrightness() {
        return state().getBrightness();
    }

    @ScriptFunction(category = "Particle", desc = "Sets the light the particle gives itself, 0 to 1")
    default void setBrightness(final float brightness) {
        state().setBrightness(brightness);
    }

    /// In ticks.
    @ScriptFunction(pure = true, category = "Particle", desc = "Ticks the particle has lived")
    int getAge();

    /// In ticks.
    @ScriptFunction(pure = true, category = "Particle", desc = "Ticks the particle lives in total")
    int getLifetime();

    @ScriptFunction(category = "Particle", desc = "Sets the ticks the particle lives in total")
    void setLifetime(int lifetime);

    @ScriptFunction(pure = true, category = "Particle", desc = "The distance from the particle to the emitter that spawned it, in blocks")
    default double distanceFromEmitter() {
        return getPosition().distanceTo(getEmitter().getWorldTransform().loc());
    }

    @ScriptFunction(pure = true, category = "Particle", desc = "The distance from the particle to where it spawned, in blocks")
    default double distanceFromSpawnPoint() {
        return getPosition().distanceTo(getSpawnPoint());
    }
}
