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
///
/// Simultaneously a component and a blueprint: [#instantiate] copies the authored component per item
/// patch, so each patch owns its own switch.
public final class AttributeModifiersComponent
    implements PatchComponent, ComponentBlueprint<AttributeModifiersComponent, ItemPatch>
{
    /// The modifier id the editor writes on every entry it authors. Entries are told apart by the
    /// attribute they sit on, so one id serves them all and the editor never asks for one.
    public static final Identifier DEFAULT_MODIFIER_ID = Akythera.identifier("item_patch_attribute");

    public static final MapCodec<AttributeModifiersComponent> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AttributeModifierEntry.CODEC.listOf().fieldOf("modifiers").forGetter(AttributeModifiersComponent::entries),
                ENABLED_CODEC.forGetter(AttributeModifiersComponent::enabled)
            ).apply(instance, AttributeModifiersComponent::new)
    );

    private final List<AttributeModifierEntry> entries;

    /// Whether this component takes part, see [PatchComponent#isEnabled]. On the authored component,
    /// the state every copy starts in.
    private boolean enabled;

    public AttributeModifiersComponent(final List<AttributeModifierEntry> entries, final boolean enabled) {
        this.entries = entries;
        this.enabled = enabled;
    }

    public List<AttributeModifierEntry> entries() {
        return entries;
    }

    public void equip(final LivingEntity equipper) {
    }

    public void unequip(final LivingEntity equipper) {
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /// Unions the two modifier lists instead of replacing. An attribute both declare keeps
    /// `overwrite`'s entry, and the switch is `overwrite`'s.
    @Override
    public PatchComponent overwrite(final PatchComponent overwrite) {
        if (!(overwrite instanceof AttributeModifiersComponent other)) {
            return overwrite;
        }

        Map<Holder<Attribute>, AttributeModifierEntry> merged = new LinkedHashMap<>();
        entries.forEach(entry -> merged.put(entry.attribute(), entry));
        other.entries.forEach(entry -> merged.put(entry.attribute(), entry));

        return new AttributeModifiersComponent(List.copyOf(merged.values()), other.enabled);
    }

    @Override
    public AttributeModifiersComponent instantiate(final ItemPatch blueprintOwner) {
        return new AttributeModifiersComponent(entries, enabled);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<AttributeModifiersComponent, ItemPatch>> mapCodec() {
        return CODEC;
    }
}
