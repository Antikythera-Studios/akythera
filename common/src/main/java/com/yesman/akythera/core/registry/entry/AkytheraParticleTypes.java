package com.yesman.akythera.core.registry.entry;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.client.api.particle.module.GlowerInitializer;
import com.yesman.akythera.client.api.particle.module.GlowerUpdater;
import com.yesman.akythera.client.api.particle.module.JitterInitializer;
import com.yesman.akythera.client.api.particle.module.PainterInitializer;
import com.yesman.akythera.client.api.particle.module.PainterUpdater;
import com.yesman.akythera.client.api.particle.module.PusherInitializer;
import com.yesman.akythera.client.api.particle.module.PusherUpdater;
import com.yesman.akythera.client.api.particle.module.Scaler2DInitializer;
import com.yesman.akythera.client.api.particle.module.Scaler2DUpdater;
import com.yesman.akythera.client.api.particle.module.Scaler3DInitializer;
import com.yesman.akythera.client.api.particle.module.Scaler3DUpdater;
import com.yesman.akythera.client.api.particle.module.ScatterInitializer;
import com.yesman.akythera.client.api.particle.module.Spinner2DInitializer;
import com.yesman.akythera.client.api.particle.module.Spinner2DUpdater;
import com.yesman.akythera.client.api.particle.module.Spinner3DInitializer;
import com.yesman.akythera.client.api.particle.module.Spinner3DUpdater;
import com.yesman.akythera.client.api.particle.module.TrailLengthInitializer;
import com.yesman.akythera.client.api.particle.module.TrailLengthUpdater;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.particle.EmitterSpawnRule;
import com.yesman.akythera.core.blueprint.particle.InstantSpawnRule;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import com.yesman.akythera.core.blueprint.particle.SkeletalMeshParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.SpawnRateRule;
import com.yesman.akythera.core.blueprint.particle.SpriteParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.StaticMeshParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailSpawnRule;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AkytheraParticleTypes {
    public static final ModPlatform.Registrar<MapCodec<? extends ParticleBlueprint>> REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.PARTICLE_TYPE_KEY, Akythera.MODID);

    public static final ModPlatform.Registrar<MapCodec<? extends EmitterSpawnRule>> SPAWN_RULE_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.EMITTER_SPAWN_RULE_TYPE_KEY, Akythera.MODID);

    public static final ModPlatform.Registrar<MapCodec<? extends ParticleInitializerBlueprint>> INITIALIZER_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.PARTICLE_INITIALIZER_TYPE_KEY, Akythera.MODID);

    public static final ModPlatform.Registrar<MapCodec<? extends ParticleUpdaterBlueprint>> UPDATER_REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.PARTICLE_UPDATER_TYPE_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends ParticleBlueprint>> SPRITE = REGISTRAR.register("sprite", () -> SpriteParticleBlueprint.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleBlueprint>> STATIC_MESH = REGISTRAR.register("static_mesh", () -> StaticMeshParticleBlueprint.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleBlueprint>> SKELETAL_MESH = REGISTRAR.register("skeletal_mesh", () -> SkeletalMeshParticleBlueprint.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleBlueprint>> TRAIL = REGISTRAR.register("trail", () -> TrailParticleBlueprint.MAP_CODEC);

    public static final Holder<MapCodec<? extends EmitterSpawnRule>> INSTANT = SPAWN_RULE_REGISTRAR.register("instant", () -> InstantSpawnRule.MAP_CODEC);

    public static final Holder<MapCodec<? extends EmitterSpawnRule>> SPAWN_RATE = SPAWN_RULE_REGISTRAR.register("spawn_rate", () -> SpawnRateRule.MAP_CODEC);

    public static final Holder<MapCodec<? extends EmitterSpawnRule>> TRAIL_SPAWN = SPAWN_RULE_REGISTRAR.register("trail", () -> TrailSpawnRule.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> CLASS_INITIALIZER = INITIALIZER_REGISTRAR.register("class", () -> ParticleInitializerBlueprint.ByClass.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> DATA_INITIALIZER = INITIALIZER_REGISTRAR.register("data", () -> ParticleInitializerBlueprint.ByData.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> SCATTER_INITIALIZER = INITIALIZER_REGISTRAR.register("scatter", () -> ScatterInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> PUSHER_INITIALIZER = INITIALIZER_REGISTRAR.register("pusher", () -> PusherInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> SPINNER_2D_INITIALIZER = INITIALIZER_REGISTRAR.register("spinner_2d", () -> Spinner2DInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> SPINNER_3D_INITIALIZER = INITIALIZER_REGISTRAR.register("spinner_3d", () -> Spinner3DInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> SCALER_2D_INITIALIZER = INITIALIZER_REGISTRAR.register("scaler_2d", () -> Scaler2DInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> SCALER_3D_INITIALIZER = INITIALIZER_REGISTRAR.register("scaler_3d", () -> Scaler3DInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> JITTER_INITIALIZER = INITIALIZER_REGISTRAR.register("jitter", () -> JitterInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> PAINTER_INITIALIZER = INITIALIZER_REGISTRAR.register("painter", () -> PainterInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> GLOWER_INITIALIZER = INITIALIZER_REGISTRAR.register("glower", () -> GlowerInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleInitializerBlueprint>> TRAIL_LENGTH_INITIALIZER = INITIALIZER_REGISTRAR.register("trail_length", () -> TrailLengthInitializer.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> CLASS_UPDATER = UPDATER_REGISTRAR.register("class", () -> ParticleUpdaterBlueprint.ByClass.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> DATA_UPDATER = UPDATER_REGISTRAR.register("data", () -> ParticleUpdaterBlueprint.ByData.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> PUSHER_UPDATER = UPDATER_REGISTRAR.register("pusher", () -> PusherUpdater.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> SCALER_2D_UPDATER = UPDATER_REGISTRAR.register("scaler_2d", () -> Scaler2DUpdater.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> SCALER_3D_UPDATER = UPDATER_REGISTRAR.register("scaler_3d", () -> Scaler3DUpdater.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> SPINNER_2D_UPDATER = UPDATER_REGISTRAR.register("spinner_2d", () -> Spinner2DUpdater.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> SPINNER_3D_UPDATER = UPDATER_REGISTRAR.register("spinner_3d", () -> Spinner3DUpdater.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> PAINTER_UPDATER = UPDATER_REGISTRAR.register("painter", () -> PainterUpdater.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> GLOWER_UPDATER = UPDATER_REGISTRAR.register("glower", () -> GlowerUpdater.MAP_CODEC);

    public static final Holder<MapCodec<? extends ParticleUpdaterBlueprint>> TRAIL_LENGTH_UPDATER = UPDATER_REGISTRAR.register("trail_length", () -> TrailLengthUpdater.MAP_CODEC);
}
