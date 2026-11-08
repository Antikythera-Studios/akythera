package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.ResourceKey;

/// The parameters of a [PlayMontageTask].
///
/// @param startSection the section the montage starts from. Empty starts from the beginning
public record PlayMontageTaskBlueprint(ResourceKey<AnimationMontage> montage, String startSection) implements BehaviorNodeBlueprint<PlayMontageTask> {
    public static final MapCodec<PlayMontageTaskBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY).fieldOf("montage").forGetter(PlayMontageTaskBlueprint::montage),
                Codec.STRING.optionalFieldOf("start_section", "").forGetter(PlayMontageTaskBlueprint::startSection)
            ).apply(instance, PlayMontageTaskBlueprint::new)
    );

    @Override
    public PlayMontageTask instantiate(final BrainComponent blueprintOwner) {
        return new PlayMontageTask(montage, startSection);
    }

    @Override
    public Class<PlayMontageTask> nodeClass() {
        return PlayMontageTask.class;
    }

    @Override
    public MapCodec<PlayMontageTaskBlueprint> mapCodec() {
        return CODEC;
    }
}
