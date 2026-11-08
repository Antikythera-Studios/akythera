package com.yesman.akythera.api.world.behavior.statemachine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BrainComponent;

/// The parameters of a [StateMachineTask].
///
/// @param stateMachine the name the asset stores the state machine under
public record StateMachineTaskBlueprint(String stateMachine) implements BehaviorNodeBlueprint<StateMachineTask> {
    public static final MapCodec<StateMachineTaskBlueprint> CODEC = Codec.STRING.fieldOf("state_machine")
        .xmap(StateMachineTaskBlueprint::new, StateMachineTaskBlueprint::stateMachine);

    @Override
    public StateMachineTask instantiate(final BrainComponent blueprintOwner) {
        return new StateMachineTask(stateMachine);
    }

    @Override
    public Class<StateMachineTask> nodeClass() {
        return StateMachineTask.class;
    }

    @Override
    public MapCodec<StateMachineTaskBlueprint> mapCodec() {
        return CODEC;
    }
}
