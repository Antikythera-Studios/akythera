package com.yesman.akythera.core.blueprint.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.module.DataDrivenParticleModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

/// The data pack entry for a [ParticleUpdateModule]: the [BlueprintBody] authored on top of it, run as a
/// [DataDrivenParticleModule].
public record ParticleUpdaterScriptBlueprint(BlueprintBody body) implements Blueprint<ParticleUpdateModule, AkytheraParticleEmitter> {
    public static final MapCodec<ParticleUpdaterScriptBlueprint> MAP_CODEC =
        BlueprintBody.MAP_CODEC.xmap(ParticleUpdaterScriptBlueprint::new, ParticleUpdaterScriptBlueprint::body);

    public static final Codec<ParticleUpdaterScriptBlueprint> CODEC = MAP_CODEC.codec();

    @Override
    @SuppressWarnings("unchecked")
    public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
        Registry<ParticleUpdaterScriptBlueprint> registry =
            StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY);

        return new DataDrivenParticleModule<>(
            (Holder.Reference<ParticleUpdaterScriptBlueprint>)registry.wrapAsHolder(this), body, emitter, ParticleUpdateModule.class
        );
    }

    @Override
    public MapCodec<ParticleUpdaterScriptBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
