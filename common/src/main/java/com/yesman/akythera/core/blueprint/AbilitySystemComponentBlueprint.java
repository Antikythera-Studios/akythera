package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

import java.util.Map;

/// Declares an [AbilitySystemComponent] on an entity patch, with the loadout it starts holding.
public record AbilitySystemComponentBlueprint(
    Map<AbilitySlot, ResourceKey<AbilityBlueprint>> loadout
) implements ComponentBlueprint<AbilitySystemComponent, EntityPatch<Entity>>
{
    public static final MapCodec<AbilitySystemComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.unboundedMap(
                        AbilitySlot.ENUM_MANAGER.getNameCodec(),
                        ResourceKey.codec(AkytheraRegistries.DataPack.ABILITY_KEY)
                    )
                    .optionalFieldOf("loadout", Map.of())
                    .forGetter(AbilitySystemComponentBlueprint::loadout)
            )
            .apply(instance, AbilitySystemComponentBlueprint::new)
    );

    /// @throws ClassCastException when the patch is not a [LivingEntityPatch], which is what an
    ///         ability system needs
    @Override
    public AbilitySystemComponent instantiate(final EntityPatch<Entity> blueprintOwner) {
        return new AbilitySystemComponent((LivingEntityPatch<?>)blueprintOwner, loadout);
    }

    @Override
    public MapCodec<AbilitySystemComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
