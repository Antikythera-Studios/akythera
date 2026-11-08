package com.yesman.akythera.core.blueprint.behavior;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/// Declares a [BrainComponent] on an entity patch. Built on the logical server only.
///
/// @param behaviorTree  the tree the brain starts with
/// @param hostileTypes  entity types the owner treats as hostile before its vanilla goals are asked
/// @param friendlyTypes entity types the owner treats as friendly before its vanilla goals are asked
/// @param enabled       whether the built [BrainComponent] starts switched on
public record BrainComponentBlueprint(
    Optional<ResourceKey<BehaviorTreeBlueprint>> behaviorTree,
    List<TagKey<EntityType<?>>> hostileTypes,
    List<TagKey<EntityType<?>>> friendlyTypes,
    boolean enabled
) implements ComponentBlueprint<BrainComponent, EntityPatch<?>>
{
    public static final MapCodec<BrainComponentBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                ResourceKey.codec(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY).optionalFieldOf("behavior_tree")
                    .forGetter(BrainComponentBlueprint::behaviorTree),
                TagKey.codec(Registries.ENTITY_TYPE).listOf().optionalFieldOf("hostile_types", List.of())
                    .forGetter(BrainComponentBlueprint::hostileTypes),
                TagKey.codec(Registries.ENTITY_TYPE).listOf().optionalFieldOf("friendly_types", List.of())
                    .forGetter(BrainComponentBlueprint::friendlyTypes),
                ENABLED_CODEC.forGetter(BrainComponentBlueprint::enabled)
            )
            .apply(instance, BrainComponentBlueprint::new)
    );

    /// A brain on an entity that is no [LivingEntity] starts switched off.
    @Override
    public BrainComponent instantiate(final EntityPatch<?> blueprintOwner) {
        BrainComponent component = new BrainComponent(blueprintOwner, behaviorTree.orElse(null), hostileTypes, friendlyTypes);
        boolean living = blueprintOwner.getOriginal() instanceof LivingEntity;

        if (!living) {
            Akythera.crashInDevOrWarnInProd("A brain needs a living entity, but " + blueprintOwner.getOriginal().getType() + " is not one");
        }

        component.setEnabled(enabled && living);

        return component;
    }

    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return !logicalClient;
    }

    @Override
    public MapCodec<BrainComponentBlueprint> mapCodec() {
        return CODEC;
    }
}
