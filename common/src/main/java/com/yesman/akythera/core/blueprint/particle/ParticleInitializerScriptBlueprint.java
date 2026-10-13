package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.module.DataDrivenParticleModule;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/// The data pack entry for a [ParticleInitializerModule]: the [BlueprintBody] authored on top of it, run as a
/// [DataDrivenParticleModule].
public record ParticleInitializerScriptBlueprint(BlueprintBody body) implements Blueprint<ParticleInitializerModule, AkytheraParticleEmitter> {
    public static final MapCodec<ParticleInitializerScriptBlueprint> MAP_CODEC =
        BlueprintBody.MAP_CODEC.xmap(ParticleInitializerScriptBlueprint::new, ParticleInitializerScriptBlueprint::body);

    public static final Codec<ParticleInitializerScriptBlueprint> CODEC = MAP_CODEC.codec();

    @Override
    @SuppressWarnings("unchecked")
    public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
        Registry<ParticleInitializerScriptBlueprint> registry =
            StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY);

        return new DataDrivenParticleModule<>(
            (Holder.Reference<ParticleInitializerScriptBlueprint>)registry.wrapAsHolder(this), body, emitter, ParticleInitializerModule.class
        );
    }

    @Override
    public MapCodec<ParticleInitializerScriptBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
