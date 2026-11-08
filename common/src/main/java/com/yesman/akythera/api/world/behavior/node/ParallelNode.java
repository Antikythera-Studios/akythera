package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.Codec;
import com.yesman.akythera.api.world.behavior.BehaviorTreeRunner;
import com.yesman.akythera.api.world.behavior.FlowControlNode;
import com.yesman.akythera.api.world.behavior.TaskStatus;
import com.yesman.akythera.api.world.behavior.TreeNode;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// Runs the leftmost child, the main one, and the other children beside it. The others start again whenever they
/// end while the main child runs. Answers what the main child answers.
public final class ParallelNode implements FlowControlNode {
    private final FinishMode finishMode;

    /// What the main child answered last.
    private TaskStatus mainStatus = TaskStatus.FAILED;

    public ParallelNode(final FinishMode finishMode) {
        this.finishMode = finishMode;
    }

    @Override
    public TaskStatus start(final BehaviorTreeRunner tree, final TreeNode placement) {
        if (placement.getChildren().isEmpty()) return TaskStatus.FAILED;

        mainStatus = tree.start(placement.getChildren().getFirst());

        if (mainStatus != TaskStatus.RUNNING) return mainStatus;

        placement.getChildren().stream().skip(1).forEach(tree::start);

        return TaskStatus.RUNNING;
    }

    @Override
    public TaskStatus update(final BehaviorTreeRunner tree, final TreeNode placement) {
        List<TreeNode> children = placement.getChildren();
        boolean mainRunning = mainStatus == TaskStatus.RUNNING;

        if (mainRunning) mainStatus = tree.update(children.getFirst());

        boolean backgroundRunning = false;

        for (TreeNode child : children.subList(1, children.size())) {
            if (child.isRunning()) {
                tree.update(child);
            } else if (mainRunning && mainStatus == TaskStatus.RUNNING) {
                tree.start(child);
            }

            backgroundRunning |= child.isRunning();
        }

        if (mainStatus == TaskStatus.RUNNING) return TaskStatus.RUNNING;

        if (finishMode == FinishMode.IMMEDIATE) {
            children.subList(1, children.size()).stream().filter(TreeNode::isRunning).forEach(tree::cancel);
        } else if (backgroundRunning) {
            return TaskStatus.RUNNING;
        }

        return mainStatus;
    }

    @Override
    public void cancel(final BehaviorTreeRunner tree, final TreeNode placement) {
        placement.getChildren().stream().filter(TreeNode::isRunning).forEach(tree::cancel);
    }

    /// When a [ParallelNode] ends once its main child ends.
    public enum FinishMode implements StringRepresentable {
        IMMEDIATE("immediate"), // cancels the other children
        DELAYED("delayed"); // waits for the other children to end

        public static final Codec<FinishMode> CODEC = StringRepresentable.fromEnum(FinishMode::values);

        private final String name;

        FinishMode(final String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
