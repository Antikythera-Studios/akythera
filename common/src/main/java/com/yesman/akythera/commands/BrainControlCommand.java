package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.yesman.akythera.api.world.behavior.BrainComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.behavior.BehaviorTreeBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.generated.LangKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

/// Sets the behavior tree of entities carrying a [BrainComponent].
public final class BrainControlCommand {
    private BrainControlCommand() {}

    private static final DynamicCommandExceptionType ERROR_TREE = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_BRAIN_UNKNOWN_TREE, value)
    );

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("brain").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("tree", ResourceKeyArgument.key(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY))
                                .executes(context -> {
                                    ResourceKey<BehaviorTreeBlueprint> tree = ResourceKeyArgument.getRegistryKey(
                                        context, "tree", AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY, ERROR_TREE
                                    );

                                    if (context.getSource().registryAccess().lookupOrThrow(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY).get(tree).isEmpty()) {
                                        throw ERROR_TREE.create(tree.identifier());
                                    }

                                    int count = 0;

                                    for (Entity entity : EntityArgument.getEntities(context, "targets")) {
                                        EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(entity);
                                        BrainComponent brain = entityPatch == null ? null : entityPatch.getComponent(BrainComponent.class);

                                        if (brain == null) continue;

                                        brain.setBehaviorTree(tree);
                                        count++;
                                    }

                                    String treeName = tree.identifier().toString();

                                    if (count == 0) {
                                        context.getSource().sendFailure(Component.translatable(LangKeys.COMMANDS_BRAIN_SET_FAILED, treeName));
                                    } else {
                                        int targets = count;
                                        context.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMANDS_BRAIN_SET_SUCCESS, treeName, targets), true);
                                    }

                                    return count;
                                })
                            )
                        )
                    )
                )
        );
    }
}
