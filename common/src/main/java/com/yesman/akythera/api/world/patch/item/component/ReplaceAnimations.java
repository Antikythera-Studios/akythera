package com.yesman.akythera.api.world.patch.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// The animation interface replacements an item imposes while it is equipped.
///
/// [#resetAnimations] declares the block the whole truth: every interface it does not name rolls back
/// to its default animation. [#mirrorAnimations] plays every animation the block names through its
/// mirrored twin, see [PlayableAnimation#mirrored].
public record ReplaceAnimations(
    List<ReplaceEntry> animationsToReplace,
    boolean resetAnimations,
    boolean mirrorAnimations
) implements UniqueComponent, ComponentBlueprint<ReplaceAnimations, ItemPatch> {
    public static final MapCodec<ReplaceAnimations> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ReplaceEntry.CODEC.listOf().fieldOf("animations_to_replace").forGetter(ReplaceAnimations::animationsToReplace),
                Codec.BOOL.fieldOf("reset_animations").forGetter(ReplaceAnimations::resetAnimations),
                Codec.BOOL.optionalFieldOf("mirror_animations", false).forGetter(ReplaceAnimations::mirrorAnimations)
            )
            .apply(instance, ReplaceAnimations::new)
    );

    @Override
    public ReplaceAnimations instantiate(final ItemPatch blueprintOwner) {
        return this;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<ReplaceAnimations, ItemPatch>> mapCodec() {
        return CODEC;
    }

    /// An interface key and the animation to play there. The key addresses any [PlayableAnimation]
    /// registry.
    public record ReplaceEntry(
        AnimationInterfaceKey interfaceKey,
        ResourceKey<? extends @NonNull PlayableAnimation> animResourceKey
    ) {
        public static final Codec<ReplaceEntry> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    AnimationInterfaceKey.ENUM_MANAGER.getNameCodec()
                        .fieldOf("anim_interface").forGetter(ReplaceEntry::interfaceKey),
                    PlayableAnimation.KEY_CODEC.fieldOf("animation").forGetter(ReplaceEntry::animResourceKey)
                )
                .apply(instance, ReplaceEntry::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, ReplaceEntry> STREAM_CODEC = StreamCodec.composite(
            AnimationInterfaceKey.ENUM_MANAGER.getStreamCodec(),
            ReplaceEntry::interfaceKey,
            PlayableAnimation.KEY_STREAM_CODEC,
            ReplaceEntry::animResourceKey,
            ReplaceEntry::new
        );
    }
}
