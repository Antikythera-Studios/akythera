package com.yesman.akythera.api.world.patch.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.component.PatchComponent;
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
///
/// Simultaneously a component and a blueprint: [#instantiate] copies the authored component per item
/// patch, so each patch owns its own switch.
public final class ReplaceAnimations implements UniqueComponent, ComponentBlueprint<ReplaceAnimations, ItemPatch> {
    public static final MapCodec<ReplaceAnimations> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ReplaceEntry.CODEC.listOf().fieldOf("animations_to_replace").forGetter(ReplaceAnimations::animationsToReplace),
                Codec.BOOL.fieldOf("reset_animations").forGetter(ReplaceAnimations::resetAnimations),
                Codec.BOOL.optionalFieldOf("mirror_animations", false).forGetter(ReplaceAnimations::mirrorAnimations),
                ENABLED_CODEC.forGetter(ReplaceAnimations::enabled)
            )
            .apply(instance, ReplaceAnimations::new)
    );

    private final List<ReplaceEntry> animationsToReplace;
    private final boolean resetAnimations;
    private final boolean mirrorAnimations;

    /// Whether this component takes part, see [PatchComponent#isEnabled]. On the authored component,
    /// the state every copy starts in.
    private boolean enabled;

    public ReplaceAnimations(
        final List<ReplaceEntry> animationsToReplace,
        final boolean resetAnimations,
        final boolean mirrorAnimations,
        final boolean enabled
    ) {
        this.animationsToReplace = animationsToReplace;
        this.resetAnimations = resetAnimations;
        this.mirrorAnimations = mirrorAnimations;
        this.enabled = enabled;
    }

    public List<ReplaceEntry> animationsToReplace() {
        return animationsToReplace;
    }

    public boolean resetAnimations() {
        return resetAnimations;
    }

    public boolean mirrorAnimations() {
        return mirrorAnimations;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public ReplaceAnimations instantiate(final ItemPatch blueprintOwner) {
        return new ReplaceAnimations(animationsToReplace, resetAnimations, mirrorAnimations, enabled);
    }

    @Override
    public boolean enabled() {
        return enabled;
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
