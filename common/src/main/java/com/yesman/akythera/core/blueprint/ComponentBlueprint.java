package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.tags.TagKey;

public interface ComponentBlueprint<T extends PatchComponent<O>, O> extends Blueprint<T, O> {
    TagKey<MapCodec<? extends ComponentBlueprint<?, ?>>> ENTITY_PATCH_COMPONENT =
        TagKey.create(AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES_KEY, Akythera.identifier("entity_patch"));

    TagKey<MapCodec<? extends ComponentBlueprint<?, ?>>> ITEM_PATCH_COMPONENT =
        TagKey.create(AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES_KEY, Akythera.identifier("item_patch"));

    @Override
    MapCodec<? extends ComponentBlueprint<T, O>> mapCodec();
}
