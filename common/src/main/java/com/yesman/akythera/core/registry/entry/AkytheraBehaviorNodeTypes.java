package com.yesman.akythera.core.registry.entry;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.ClassNodeBlueprint;
import com.yesman.akythera.api.world.behavior.node.ActivateAbilityTaskBlueprint;
import com.yesman.akythera.api.world.behavior.node.BlackboardCondition;
import com.yesman.akythera.api.world.behavior.node.FloatTask;
import com.yesman.akythera.api.world.behavior.node.MoveToTaskBlueprint;
import com.yesman.akythera.api.world.behavior.node.ParallelNodeBlueprint;
import com.yesman.akythera.api.world.behavior.node.PlayMontageTaskBlueprint;
import com.yesman.akythera.api.world.behavior.node.SelectorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.node.SequenceNodeBlueprint;
import com.yesman.akythera.api.world.behavior.node.WaitTaskBlueprint;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorConditionBlueprint;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorSubtaskBlueprint;
import com.yesman.akythera.api.world.behavior.script.DataDrivenBehaviorTaskBlueprint;
import com.yesman.akythera.api.world.behavior.statemachine.StateMachineTaskBlueprint;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AkytheraBehaviorNodeTypes {
    private AkytheraBehaviorNodeTypes() {}

    public static final ModPlatform.Registrar<MapCodec<? extends BehaviorNodeBlueprint<?>>> REGISTRAR =
        ModPlatformProvider.get().getRegistrar(AkytheraRegistries.Frozen.BEHAVIOR_NODE_TYPE_KEY, Akythera.MODID);

    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> CLASS = REGISTRAR.register("class", () -> ClassNodeBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> DATA_TASK = REGISTRAR.register("data_task", () -> DataDrivenBehaviorTaskBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> DATA_SUBTASK = REGISTRAR.register("data_subtask", () -> DataDrivenBehaviorSubtaskBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> DATA_CONDITION = REGISTRAR.register("data_condition", () -> DataDrivenBehaviorConditionBlueprint.CODEC);

    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> SELECTOR = REGISTRAR.register("selector", () -> SelectorNodeBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> SEQUENCE = REGISTRAR.register("sequence", () -> SequenceNodeBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> PARALLEL = REGISTRAR.register("parallel", () -> ParallelNodeBlueprint.CODEC);

    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> WAIT = REGISTRAR.register("wait", () -> WaitTaskBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> MOVE_TO = REGISTRAR.register("move_to", () -> MoveToTaskBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> FLOAT = REGISTRAR.register("float", () -> FloatTask.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> PLAY_MONTAGE = REGISTRAR.register("play_montage", () -> PlayMontageTaskBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> ACTIVATE_ABILITY = REGISTRAR.register("activate_ability", () -> ActivateAbilityTaskBlueprint.CODEC);
    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> STATE_MACHINE = REGISTRAR.register("state_machine", () -> StateMachineTaskBlueprint.CODEC);

    public static final Holder<MapCodec<? extends BehaviorNodeBlueprint<?>>> BLACKBOARD = REGISTRAR.register("blackboard", () -> BlackboardCondition.CODEC);
}
