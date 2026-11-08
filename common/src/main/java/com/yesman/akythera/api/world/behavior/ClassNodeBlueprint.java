package com.yesman.akythera.api.world.behavior;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;

import java.lang.reflect.InvocationTargetException;

/// A node class with a public no-argument constructor, named by its [ScriptType]. Each brain gets a new instance.
public record ClassNodeBlueprint(ScriptType type) implements BehaviorNodeBlueprint<BehaviorNode> {
    public static final MapCodec<ClassNodeBlueprint> CODEC = AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec()
        .validate(type -> BehaviorNode.class.isAssignableFrom(type.type())
            ? DataResult.success(type)
            : DataResult.error(() -> type.type().getSimpleName() + " is no " + BehaviorNode.class.getSimpleName()))
        .fieldOf("class")
        .xmap(ClassNodeBlueprint::new, ClassNodeBlueprint::type);

    /// @throws IllegalStateException when the class can't be built
    @Override
    public BehaviorNode instantiate(final BrainComponent blueprintOwner) {
        try {
            return (BehaviorNode)type.type().getConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Failed at building behavior node " + type.type().getSimpleName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends BehaviorNode> nodeClass() {
        return (Class<? extends BehaviorNode>)type.type();
    }

    @Override
    public MapCodec<ClassNodeBlueprint> mapCodec() {
        return CODEC;
    }
}
