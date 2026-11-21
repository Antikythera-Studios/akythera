package com.yesman.akythera.api.world.ability;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.core.blueprint.AbilityResourceBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/// An [AbilityResourceScript] named by its class or by its data pack entry. [ComponentsFactory#getAbilityResource]
/// builds what it names.
public sealed interface AbilityResourceRef {
    Codec<AbilityResourceRef> CODEC = Codec.STRING.dispatch(
        ref -> switch (ref) {
            case ByClass _ -> "class";
            case ByData _ -> "data";
        },
        literal -> switch (literal) {
            case "class" -> ByClass.MAP_CODEC;
            case "data" -> ByData.MAP_CODEC;
            default -> throw new IllegalArgumentException("Unknown ability resource kind " + literal);
        }
    );

    StreamCodec<ByteBuf, AbilityResourceRef> STREAM_CODEC = ByteBufCodecs.either(
        ResourceKey.streamCodec(AkytheraRegistries.DataPack.ABILITY_RESOURCE_KEY),
        Identifier.STREAM_CODEC.<ScriptType>map(
            id -> AkytheraRegistries.Frozen.SCRIPT_TYPE.getOptional(id).orElseThrow(() -> new IllegalArgumentException("No script type " + id)),
            AkytheraRegistries.Frozen.SCRIPT_TYPE::getKey
        )
    ).map(
        either -> either.map(ByData::new, ByClass::new),
        ref -> switch (ref) {
            case ByData byData -> Either.left(byData.key());
            case ByClass byClass -> Either.right(byClass.type());
        }
    );

    /// An [AbilityResourceScript] class with a public no-argument constructor.
    record ByClass(ScriptType type) implements AbilityResourceRef {
        static final MapCodec<ByClass> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("id").forGetter(ByClass::type)
                ).apply(instance, ByClass::new)
        );
    }

    /// An [AbilityResourceBlueprint] data pack entry, or the [AbilityResourceScript] class registered under its id,
    /// which wins it ([ScriptType#classOf]).
    record ByData(ResourceKey<AbilityResourceBlueprint> key) implements AbilityResourceRef {
        static final MapCodec<ByData> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance ->
                instance.group(
                    ResourceKey.codec(AkytheraRegistries.DataPack.ABILITY_RESOURCE_KEY).fieldOf("id").forGetter(ByData::key)
                ).apply(instance, ByData::new)
        );
    }
}
