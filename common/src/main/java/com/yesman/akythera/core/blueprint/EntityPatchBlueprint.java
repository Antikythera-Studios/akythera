package com.yesman.akythera.core.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.blueprint.script.DataDrivenEntityPatchScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Function;

public record EntityPatchBlueprint(
    List<ComponentBlueprint<?, ?>> components,
    FlowNode onConstruct,
    FlowNode onJoinWorld,
    FlowNode update,
    List<ScriptVariables.VariableInfo> variables
) implements Blueprint<DataDrivenEntityPatchScript, EntityPatch<Entity>>
{
    public static final MapCodec<EntityPatchBlueprint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES.byNameCodec()
                    .<ComponentBlueprint<?, ?>>dispatch(ComponentBlueprint::mapCodec, Function.identity())
                    .listOf().fieldOf("components").forGetter(EntityPatchBlueprint::components),
                FlowNode.CODEC.fieldOf("on_construct").forGetter(EntityPatchBlueprint::onConstruct),
                FlowNode.CODEC.fieldOf("on_join_world").forGetter(EntityPatchBlueprint::onJoinWorld),
                FlowNode.CODEC.fieldOf("update").forGetter(EntityPatchBlueprint::update),
                ScriptVariables.VariableInfo.CODEC.listOf().fieldOf("variables").forGetter(EntityPatchBlueprint::variables)
            ).apply(instance, EntityPatchBlueprint::new)
    );

    public static final Codec<EntityPatchBlueprint> CODEC = MAP_CODEC.codec();

    @Override
    public DataDrivenEntityPatchScript instantiate(final EntityPatch<Entity> blueprintOwner) {
        Registry<EntityPatchBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY);
        return new DataDrivenEntityPatchScript((Holder.Reference<EntityPatchBlueprint>)registry.wrapAsHolder(this), blueprintOwner);
    }

    @Override
    public MapCodec<EntityPatchBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
