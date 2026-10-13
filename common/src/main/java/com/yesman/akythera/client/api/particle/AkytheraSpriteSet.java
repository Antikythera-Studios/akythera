package com.yesman.akythera.client.api.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Set;

/// Particle atlas sprites looked up by id on every read, so a resource reload never leaves a stale sprite behind.
///
/// @param sprites particle atlas sprite ids, in animation order. Never empty
public record AkytheraSpriteSet(List<Identifier> sprites) implements SpriteSet {
    @Override
    public TextureAtlasSprite get(final int index, final int max) {
        return particleAtlas().getSprite(spriteId(index, max));
    }

    /// The id of the sprite [#get(int, int)] answers: `index` of `max` cut into equal shares, one per sprite.
    public Identifier spriteId(final int index, final int max) {
        return sprites.get(Math.min(index * sprites.size() / max, sprites.size() - 1));
    }

    @Override
    public TextureAtlasSprite get(final RandomSource random) {
        return particleAtlas().getSprite(sprites.get(random.nextInt(sprites.size())));
    }

    @Override
    public TextureAtlasSprite first() {
        return particleAtlas().getSprite(sprites.getFirst());
    }

    /// The sprites the vanilla particle type `id` draws with, rebound by vanilla on every resource reload.
    ///
    /// @throws IllegalStateException when `id` names no particle type with sprites, such as `minecraft:block`
    public static SpriteSet vanilla(final Identifier id) {
        SpriteSet sprites = Minecraft.getInstance().particleEngine.resourceManager.spriteSets.get(id);

        if (sprites == null) throw new IllegalStateException("The vanilla particle type " + id + " has no sprites");

        return sprites;
    }

    /// The ids of the vanilla particle types that have sprites.
    public static Set<Identifier> vanillaIds() {
        return Set.copyOf(Minecraft.getInstance().particleEngine.resourceManager.spriteSets.keySet());
    }

    private static TextureAtlas particleAtlas() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
    }
}
