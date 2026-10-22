package com.yesman.akythera.client.api.particle;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// One play of a [ParticleSystemBlueprint]: a particle that draws nothing and ticks its emitters. Removed once every
/// emitter finished, or when stopped.
@ScriptType
public class AkytheraParticleSystem extends NoRenderParticle {
    private final ParticleSystemBlueprint blueprint;

    /// The socket the system follows, or null for a system that stays where it was played. The system stops once the
    /// socket is gone.
    private final @Nullable ParticleAttachment attachment;

    /// The system's transform in world space.
    private Transform transform;

    /// [#transform] one tick back.
    private Transform previousTransform;

    /// The parameters this play passes the emitters, by the names the blueprint declares.
    private final ScriptVariables parameters;

    /// A copy of [#parameters] the built-in modules' parameters read as locals, taken again whenever a parameter
    /// changes.
    private ScriptVariables moduleLocals;

    private final List<AkytheraParticleEmitter> emitters;

    /// Where the emitters put their particles and look up the pieces they name.
    private final ParticleHost host;

    /// Plays in `level`, through [ParticleHost#LEVEL].
    ///
    /// @param transform  where the system starts, in world space
    /// @param attachment the socket the system follows from its first tick, or null
    public AkytheraParticleSystem(
        final ClientLevel level, final ParticleSystemBlueprint blueprint, final Transform transform, final @Nullable ParticleAttachment attachment
    ) {
        this(level, blueprint, transform, attachment, ParticleHost.LEVEL);
    }

    /// @param level      null where no level is loaded, such as an editor preview. Particles then collide through
    ///                   [ParticleHost#collide]
    /// @param transform  where the system starts, in world space
    /// @param attachment the socket the system follows from its first tick, or null
    public AkytheraParticleSystem(
        final @Nullable ClientLevel level,
        final ParticleSystemBlueprint blueprint,
        final Transform transform,
        final @Nullable ParticleAttachment attachment,
        final ParticleHost host
    ) {
        super(level, transform.loc().x, transform.loc().y, transform.loc().z);

        this.blueprint = blueprint;
        this.host = host;
        this.attachment = attachment;
        this.transform = transform;
        this.previousTransform = transform;
        this.parameters = new ScriptVariables(blueprint.variables());
        this.moduleLocals = this.parameters.snapshot();
        this.emitters = blueprint.emitters().stream().map(emitter -> new AkytheraParticleEmitter(this, emitter)).toList();
    }

    /// Follows the attachment, stopping once its socket is gone, then ticks the emitters.
    @Override
    public void tick() {
        previousTransform = transform;

        if (attachment != null) {
            Transform socketTransform = attachment.worldTransform(level, 1.0F);

            if (socketTransform == null) {
                remove();

                return;
            }

            transform = socketTransform;
        }

        float seconds = age++ / 20.0F;

        emitters.forEach(emitter -> emitter.tick(seconds));

        if (emitters.stream().allMatch(AkytheraParticleEmitter::isFinished)) remove();
    }

    @Override
    public void remove() {
        super.remove();
        emitters.forEach(AkytheraParticleEmitter::finish);
    }

    /// Moves the play to `seconds` since it started, as if it had played there. The particles already spawned stay.
    public void seek(final float seconds) {
        age = Math.round(seconds * 20.0F);
        emitters.forEach(emitter -> emitter.seek(seconds));
    }

    @ScriptFunction(category = "Particle", desc = "Stops the particle system. Emitters that hide their particles on finish remove them")
    public void stop() {
        remove();
    }

    /// Sets the parameter `name`. An unknown name is ignored.
    @ScriptFunction(category = "Particle", desc = "Sets a parameter of the particle system")
    public void setParameter(final String name, final Object value) {
        if (parameters.hasVariable(name)) {
            parameters.setValue(name, value);
            moduleLocals = parameters.snapshot();
        } else {
            Akythera.LOGGER.warn("Particle system has no parameter named {}", name);
        }
    }

    /// Sets each parameter `tag` names, decoded by the parameter's type. Entries that name no parameter, or do not
    /// decode, are skipped.
    @SuppressWarnings("unchecked")
    public void setParameters(final CompoundTag tag) {
        tag.forEach((name, value) -> {
            ScriptVariables.VariableInfo info = blueprint.variables().stream().filter(variable -> variable.name().equals(name)).findFirst().orElse(null);

            if (info == null || info.type().codec() == null) {
                Akythera.LOGGER.warn("Particle system has no parameter named {} that decodes", name);

                return;
            }

            ((Codec<Object>)info.type().codec()).parse(NbtOps.INSTANCE, value)
                .ifSuccess(decoded -> parameters.setValue(name, decoded))
                .ifError(error -> Akythera.LOGGER.warn("Particle system parameter {}: {}", name, error.message()));
        });

        moduleLocals = parameters.snapshot();
    }

    public ParticleSystemBlueprint getBlueprint() {
        return blueprint;
    }

    public Transform getTransform() {
        return transform;
    }

    /// Places the system at `transform`, in world space. A system following a socket takes the socket's again at its
    /// next tick.
    public void moveTo(final Transform transform) {
        this.transform = transform;
    }

    /// The transform between the last tick's (`delta` 0) and this tick's (`delta` 1).
    public Transform getTransformAt(final float delta) {
        return Transform.slerp(previousTransform, transform, delta);
    }

    public @Nullable ParticleAttachment getAttachment() {
        return attachment;
    }

    public ScriptVariables getParameters() {
        return parameters;
    }

    public ScriptVariables getModuleLocals() {
        return moduleLocals;
    }

    /// Null where no level is loaded, such as an editor preview.
    public @Nullable ClientLevel getLevel() {
        return level;
    }

    public ParticleHost getHost() {
        return host;
    }

    public List<AkytheraParticleEmitter> getEmitters() {
        return emitters;
    }
}
