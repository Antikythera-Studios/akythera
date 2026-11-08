package com.yesman.akythera.api.world.behavior.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.world.behavior.BehaviorCondition;
import com.yesman.akythera.api.world.behavior.BehaviorNodeBlueprint;
import com.yesman.akythera.api.world.behavior.BlackboardKey;
import com.yesman.akythera.api.world.behavior.BrainComponent;

import java.util.stream.Stream;

/// Passes while `key` is set, or while it is not when `isSet` is false. A boolean is set while true, any other value
/// while not null. Keeps no state, so every brain shares one.
public record BlackboardCondition(BlackboardKey key, boolean isSet) implements BehaviorCondition, BehaviorNodeBlueprint<BlackboardCondition> {
    public static final MapCodec<BlackboardCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                BlackboardKey.CODEC.fieldOf("key").forGetter(BlackboardCondition::key),
                Codec.BOOL.optionalFieldOf("is_set", true).forGetter(BlackboardCondition::isSet)
            ).apply(instance, BlackboardCondition::new)
    );

    @Override
    public boolean test(final BrainComponent brain) {
        Object value = brain.getBlackboardValue(key);
        boolean set = value instanceof Boolean bool ? bool : value != null;

        return set == isSet;
    }

    @Override
    public BlackboardCondition instantiate(final BrainComponent blueprintOwner) {
        return this;
    }

    @Override
    public Class<BlackboardCondition> nodeClass() {
        return BlackboardCondition.class;
    }

    @Override
    public Stream<BlackboardKey> blackboardKeys() {
        return Stream.of(key);
    }

    @Override
    public MapCodec<BlackboardCondition> mapCodec() {
        return CODEC;
    }
}
