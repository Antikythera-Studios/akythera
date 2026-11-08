package com.yesman.akythera.core.blueprint.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.behavior.BehaviorTreeInstance;
import com.yesman.akythera.api.world.behavior.BlackboardKey;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.behavior.PerceptionSettings;
import com.yesman.akythera.api.world.behavior.TreeNodeBlueprint;
import com.yesman.akythera.api.world.behavior.script.BehaviorTreeScript;
import com.yesman.akythera.api.world.behavior.statemachine.BehaviorStateMachineData;
import com.yesman.akythera.api.world.behavior.statemachine.StateMachineTask;
import com.yesman.akythera.core.blueprint.Blueprint;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The data pack entry of a mob behavior tree asset. The body's variables are the blackboard, and its event graph
/// implements [BehaviorTreeScript].
///
/// @param roots         the placements ROOT runs, left to right by priority like a selector. A tree with none does
///                      nothing
/// @param stateMachines the state machines a [StateMachineTask] of this asset runs, by name
public record BehaviorTreeBlueprint(
    BlueprintBody body,
    List<TreeNodeBlueprint> roots,
    Map<String, BehaviorStateMachineData> stateMachines,
    PerceptionSettings perception
) implements Blueprint<BehaviorTreeInstance, BrainComponent> {
    /// Fails an asset whose nodes name a [BlackboardKey] its variables do not declare with the same type.
    public static final MapCodec<BehaviorTreeBlueprint> MAP_CODEC = RecordCodecBuilder.<BehaviorTreeBlueprint>mapCodec(
        instance ->
            instance.group(
                BlueprintBody.MAP_CODEC.forGetter(BehaviorTreeBlueprint::body),
                TreeNodeBlueprint.CODEC.listOf().optionalFieldOf("roots", List.of()).forGetter(BehaviorTreeBlueprint::roots),
                Codec.unboundedMap(Codec.STRING, BehaviorStateMachineData.CODEC).optionalFieldOf("state_machines", Map.of())
                    .forGetter(BehaviorTreeBlueprint::stateMachines),
                PerceptionSettings.CODEC.optionalFieldOf("perception", PerceptionSettings.NONE).forGetter(BehaviorTreeBlueprint::perception)
            ).apply(instance, BehaviorTreeBlueprint::new)
    ).validate(blueprint -> {
        List<TreeNodeBlueprint> trees = new ArrayList<>();
        List<BlackboardKey> keys = new ArrayList<>();

        trees.addAll(blueprint.roots());
        blueprint.stateMachines().values().forEach(stateMachine -> stateMachine.forEachTree(trees::add));
        trees.forEach(tree -> tree.forEachNode(node -> node.blackboardKeys().forEach(keys::add)));

        for (BlackboardKey key : keys) {
            Optional<ScriptVariables.VariableInfo> variable = blueprint.body().variables().stream()
                .filter(declared -> declared.name().equals(key.name()))
                .findFirst();

            if (variable.isEmpty()) return DataResult.error(() -> "The behavior tree declares no variable " + key.name());
            if (!variable.get().type().equals(key.type())) {
                return DataResult.error(() -> "The behavior tree variable " + key.name() + " is not of the key's type");
            }
        }

        return DataResult.success(blueprint);
    });

    public static final Codec<BehaviorTreeBlueprint> CODEC = MAP_CODEC.codec();

    public BehaviorTreeBlueprint {
        roots = List.copyOf(roots);
        stateMachines = Map.copyOf(stateMachines);
    }

    /// The asset built for `blueprintOwner`.
    ///
    /// @throws IllegalStateException when a data pack node of the tree names a missing entry
    @Override
    @SuppressWarnings("unchecked")
    public BehaviorTreeInstance instantiate(final BrainComponent blueprintOwner) {
        Registry<BehaviorTreeBlueprint> registry = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY);

        return new BehaviorTreeInstance(blueprintOwner, (Holder.Reference<BehaviorTreeBlueprint>)registry.wrapAsHolder(this));
    }

    @Override
    public MapCodec<BehaviorTreeBlueprint> mapCodec() {
        return MAP_CODEC;
    }
}
