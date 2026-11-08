package com.yesman.akythera.api.world.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.function.Consumer;

/// One placement of a task or a flow control node in a behavior tree, with the conditions and subtasks attached
/// to it. Builds a [TreeNode] and everything under it for one brain.
///
/// @param children   the placements a [FlowControlNode] runs, empty under a [BehaviorTask]
/// @param conditions tested before the placement starts, and while it runs as [#abortMode] says
/// @param subtasks   run while the placement is [TaskStatus#RUNNING]
public record TreeNodeBlueprint(
    BehaviorNodeBlueprint<?> node,
    List<TreeNodeBlueprint> children,
    List<Condition> conditions,
    List<Subtask> subtasks,
    AbortMode abortMode
) {
    public static final Codec<TreeNodeBlueprint> CODEC = Codec.recursive("TreeNode", self ->
        RecordCodecBuilder.<TreeNodeBlueprint>create(
            instance ->
                instance.group(
                    BehaviorNodeBlueprint.CODEC.fieldOf("node").forGetter(TreeNodeBlueprint::node),
                    self.listOf().optionalFieldOf("children", List.of()).forGetter(TreeNodeBlueprint::children),
                    Condition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(TreeNodeBlueprint::conditions),
                    Subtask.CODEC.listOf().optionalFieldOf("subtasks", List.of()).forGetter(TreeNodeBlueprint::subtasks),
                    AbortMode.CODEC.optionalFieldOf("abort_mode", AbortMode.NONE).forGetter(TreeNodeBlueprint::abortMode)
                ).apply(instance, TreeNodeBlueprint::new)
        ).validate(placement -> {
            Class<?> node = placement.node().nodeClass();

            if (BehaviorTask.class.isAssignableFrom(node)) {
                return placement.children().isEmpty()
                    ? DataResult.success(placement)
                    : DataResult.error(() -> "A task takes no children: " + node.getSimpleName());
            }

            return FlowControlNode.class.isAssignableFrom(node)
                ? DataResult.success(placement)
                : DataResult.error(() -> node.getSimpleName() + " is no task or flow control node");
        })
    );

    public TreeNodeBlueprint {
        children = List.copyOf(children);
        conditions = List.copyOf(conditions);
        subtasks = List.copyOf(subtasks);
    }

    /// Visits the blueprint of every node of this placement and those under it, depth-first.
    public void forEachNode(final Consumer<BehaviorNodeBlueprint<?>> visitor) {
        visitor.accept(node);
        conditions.forEach(condition -> visitor.accept(condition.condition()));
        subtasks.forEach(subtask -> visitor.accept(subtask.subtask()));
        children.forEach(child -> child.forEachNode(visitor));
    }

    /// This placement and those under it built for `blueprintOwner`.
    ///
    /// @throws IllegalStateException when a data pack node names a missing entry
    public TreeNode instantiate(final BrainComponent blueprintOwner) {
        return new TreeNode(
            node.instantiate(blueprintOwner),
            children.stream().map(child -> child.instantiate(blueprintOwner)).toList(),
            conditions.stream().map(condition -> new TreeNode.Condition(condition.condition().instantiate(blueprintOwner))).toList(),
            subtasks.stream().map(subtask -> new TreeNode.Subtask(subtask.subtask().instantiate(blueprintOwner), subtask.interval())).toList(),
            abortMode
        );
    }

    /// A [BehaviorCondition] attached to a placement.
    public record Condition(BehaviorNodeBlueprint<? extends BehaviorCondition> condition) {
        static final Codec<Condition> CODEC = BehaviorNodeBlueprint.kindCodec(BehaviorCondition.class)
            .fieldOf("node")
            .xmap(Condition::new, Condition::condition)
            .codec();
    }

    /// A [BehaviorSubtask] attached to a placement.
    ///
    /// @param interval server ticks between two [BehaviorSubtask#onTick] calls
    public record Subtask(BehaviorNodeBlueprint<? extends BehaviorSubtask> subtask, int interval) {
        static final Codec<Subtask> CODEC = RecordCodecBuilder.create(
            instance ->
                instance.group(
                    BehaviorNodeBlueprint.kindCodec(BehaviorSubtask.class).fieldOf("node").forGetter(Subtask::subtask),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("interval", 10).forGetter(Subtask::interval)
                ).apply(instance, Subtask::new)
        );
    }
}
