package com.yesman.akythera.api.world.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;

/// A variable of a behavior tree asset, which every brain running the asset holds in its blackboard.
///
/// @param name the variable's name
/// @param type the variable's type, tested against the asset's variables when the asset loads
public record BlackboardKey(String name, ScriptType type) {
    public static final Codec<BlackboardKey> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("name").forGetter(BlackboardKey::name),
                ScriptType.CODEC.fieldOf("type").forGetter(BlackboardKey::type)
            ).apply(instance, BlackboardKey::new)
    );
}
