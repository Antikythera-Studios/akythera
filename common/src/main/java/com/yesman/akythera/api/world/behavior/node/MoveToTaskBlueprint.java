package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BlackboardKey;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.stream.Stream;

/// The parameters of a [MoveToTask].
///
/// @param acceptanceRadius the distance at which the task succeeds, in blocks
/// @param speedModifier    the speed passed to [PathNavigation#moveTo]
public record MoveToTaskBlueprint(MoveToTask.Destination destination, float acceptanceRadius, double speedModifier)
    implements BehaviorNodeBlueprint<MoveToTask>
{
    public static final MapCodec<MoveToTaskBlueprint> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                MoveToTask.Destination.CODEC.fieldOf("destination").forGetter(MoveToTaskBlueprint::destination),
                Codec.FLOAT.optionalFieldOf("acceptance_radius", 1.0F).forGetter(MoveToTaskBlueprint::acceptanceRadius),
                Codec.DOUBLE.optionalFieldOf("speed_modifier", 1.0D).forGetter(MoveToTaskBlueprint::speedModifier)
            ).apply(instance, MoveToTaskBlueprint::new)
    );

    @Override
    public MoveToTask instantiate(final BrainComponent blueprintOwner) {
        return new MoveToTask(destination, acceptanceRadius, speedModifier);
    }

    @Override
    public Class<MoveToTask> nodeClass() {
        return MoveToTask.class;
    }

    @Override
    public Stream<BlackboardKey> blackboardKeys() {
        return destination instanceof MoveToTask.Destination.Blackboard blackboard ? Stream.of(blackboard.key()) : Stream.empty();
    }

    @Override
    public MapCodec<MoveToTaskBlueprint> mapCodec() {
        return CODEC;
    }
}
