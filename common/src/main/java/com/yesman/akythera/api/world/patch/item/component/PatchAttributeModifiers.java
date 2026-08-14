package com.yesman.akythera.api.world.patch.item.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/// A component used by [ItemPatch] to modify entity attributes when equip it
public record PatchAttributeModifiers(List<AttributeModifierEntry> entries)
    implements PatchComponent, ComponentBlueprint<PatchAttributeModifiers, Void>
{
    public static final MapCodec<PatchAttributeModifiers> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AttributeModifierEntry.CODEC.listOf().fieldOf("modifiers").forGetter(PatchAttributeModifiers::entries)
            ).apply(instance, PatchAttributeModifiers::new)
    );

    public void equip(final LivingEntity equipper) {

    }

    public void unequip(final LivingEntity equipper) {

    }

    @Override
    public PatchAttributeModifiers instantiate(final Void blueprintOwner) {
        return this;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<PatchAttributeModifiers, Void>> mapCodec() {
        return CODEC;
    }
}
