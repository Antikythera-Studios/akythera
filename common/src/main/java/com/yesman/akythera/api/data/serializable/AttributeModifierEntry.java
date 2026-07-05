package com.yesman.akythera.api.data.serializable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.NotNull;

public record AttributeModifierEntry(Holder<@NotNull Attribute> attribute, AttributeModifier modifier) {
    public static final Codec<AttributeModifierEntry> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance.group(
                    Attribute.CODEC.fieldOf("attribute").forGetter(AttributeModifierEntry::attribute),
                    AttributeModifier.MAP_CODEC.forGetter(AttributeModifierEntry::modifier)
                )
                .apply(instance, AttributeModifierEntry::new)
        );
}