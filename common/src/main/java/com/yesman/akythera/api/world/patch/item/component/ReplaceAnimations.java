package com.yesman.akythera.api.world.patch.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

import java.util.List;

public record ReplaceAnimations(List<Replace> animationsToReplace, boolean resetAnimations)
    implements PatchComponent, ComponentBlueprint<ReplaceAnimations, Void>
{
    public static final MapCodec<ReplaceAnimations> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Replace.CODEC.listOf().fieldOf("animationsToReplace").forGetter(ReplaceAnimations::animationsToReplace),
                Codec.BOOL.fieldOf("reset_animations").forGetter(ReplaceAnimations::resetAnimations)
            )
            .apply(instance, ReplaceAnimations::new)
    );

    public void replaceAnimations(final BlendingAnimator animator) {
        if (resetAnimations) animator.resetAllInterfaces();
        animator.replaceAnimation(animationsToReplace);
    }

    @Override
    public ReplaceAnimations instantiate(final Void blueprintOwner) {
        return this;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<ReplaceAnimations, Void>> mapCodec() {
        return CODEC;
    }

    public record Replace(AnimationInterfaceKey interfaceKey, ResourceKey<AnimationSequence> animResourceKey) {
        public static final Codec<Replace> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AnimationInterfaceKey.ENUM_MANAGER.getNameCodec()
                        .fieldOf("anim_interface").forGetter(Replace::interfaceKey),
                    ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY)
                        .fieldOf("animation").forGetter(Replace::animResourceKey)
                )
                .apply(instance, Replace::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, Replace> STREAM_CODEC = StreamCodec.composite(
            AnimationInterfaceKey.ENUM_MANAGER.getStreamCodec(),
            Replace::interfaceKey,
            ResourceKey.streamCodec(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY),
            Replace::animResourceKey,
            Replace::new
        );
    }
}
