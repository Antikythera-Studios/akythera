package com.yesman.akythera.api.world.patch.item.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A component used by [ItemPatch] to modify entity attributes when equip it
public record AttributeModifiersComponent(List<AttributeModifierEntry> entries)
    implements PatchComponent, ComponentBlueprint<AttributeModifiersComponent, ItemPatch>
{
    /// The modifier id the editor writes on every entry it authors. Entries are told apart by the
    /// attribute they sit on, so one id serves them all and the editor never asks for one.
    public static final Identifier DEFAULT_MODIFIER_ID = Akythera.identifier("item_patch_attribute");

    public static final MapCodec<AttributeModifiersComponent> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AttributeModifierEntry.CODEC.listOf().fieldOf("modifiers").forGetter(AttributeModifiersComponent::entries)
            ).apply(instance, AttributeModifiersComponent::new)
    );

    public void equip(final LivingEntity equipper) {
    }

    public void unequip(final LivingEntity equipper) {
    }

    /// Unions the two modifier lists instead of replacing. An attribute both declare keeps
    /// `overwrite`'s entry.
    @Override
    public PatchComponent overwrite(final PatchComponent overwrite) {
        if (!(overwrite instanceof AttributeModifiersComponent other)) {
            return overwrite;
        }

        Map<Holder<Attribute>, AttributeModifierEntry> merged = new LinkedHashMap<>();
        entries.forEach(entry -> merged.put(entry.attribute(), entry));
        other.entries.forEach(entry -> merged.put(entry.attribute(), entry));

        return new AttributeModifiersComponent(List.copyOf(merged.values()));
    }

    @Override
    public AttributeModifiersComponent instantiate(final ItemPatch blueprintOwner) {
        return this;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<AttributeModifiersComponent, ItemPatch>> mapCodec() {
        return CODEC;
    }
}
