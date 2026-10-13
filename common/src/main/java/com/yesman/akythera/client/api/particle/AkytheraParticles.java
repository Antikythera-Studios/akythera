package com.yesman.akythera.client.api.particle;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.reflection.ScriptFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Plays [ParticleSystemBlueprint]s in the client level, from code and node graphs.
public final class AkytheraParticles {
    private AkytheraParticles() {}

    /// Plays `blueprint` at `transform` in the client level, following `attachment` from its first tick when given.
    /// Null without a level, or off the render thread, where an integrated server runs its scripts.
    public static @Nullable AkytheraParticleSystem play(
        final ParticleSystemBlueprint blueprint, final Transform transform, final @Nullable ParticleAttachment attachment
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || !minecraft.isSameThread()) return null;

        AkytheraParticleSystem system = new AkytheraParticleSystem(minecraft.level, blueprint, transform, attachment);
        minecraft.particleEngine.add(system);

        return system;
    }

    @ScriptFunction(label = "Play Particle", category = "Particle", desc = "Plays a particle system at a position for the local player. Null on the server")
    public static @Nullable AkytheraParticleSystem play(final ParticleSystemBlueprint blueprint, final Vec3 position) {
        return blueprint == null || position == null ? null : play(blueprint, Transform.fromTranslation(position), null);
    }

    @ScriptFunction(label = "Play Particle Attached", category = "Particle", desc = """
        Plays a particle system that follows a socket of an entity, or of the item it holds (mainhand/<socket>), for the
        local player. Null on the server or when the socket can't be found
        """)
    public static @Nullable AkytheraParticleSystem playAttached(
        final ParticleSystemBlueprint blueprint, final EntityPatch<?> entity, final String socketPath
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (blueprint == null || entity == null || socketPath == null || minecraft.level == null) return null;

        ParticleAttachment attachment = new ParticleAttachment(entity.getOriginal().getId(), socketPath);
        Transform transform = attachment.worldTransform(minecraft.level);

        return transform == null ? null : play(blueprint, transform, attachment);
    }
}
