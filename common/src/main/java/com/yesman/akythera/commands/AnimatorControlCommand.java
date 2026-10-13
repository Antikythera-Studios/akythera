package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

public class AnimatorControlCommand {
    private static final DynamicCommandExceptionType ERROR_ANIM_MONTAGE = new DynamicCommandExceptionType(
        value -> Component.literal("Invalid animation montage: " + value)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("animator").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("play_montage")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("animation", ResourceKeyArgument.key(AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY))
                                .executes(commandContext ->
                                    playAnimation(
                                        EntityArgument.getEntities(commandContext, "targets"),
                                        ResourceKeyArgument.getRegistryKey(commandContext, "animation", AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, ERROR_ANIM_MONTAGE),
                                        AnimationMontage.GENERIC_SECTION_NAME
                                    )
                                )
                                .then(Commands.argument("section", StringArgumentType.string())
                                    .executes(commandContext ->
                                        playAnimation(
                                            EntityArgument.getEntities(commandContext, "targets"),
                                            ResourceKeyArgument.getRegistryKey(commandContext, "animation", AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY, ERROR_ANIM_MONTAGE),
                                            StringArgumentType.getString(commandContext, "section")
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("soft_pause")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("paused", BoolArgumentType.bool())
                                .executes(commandContext ->
                                    softPause(EntityArgument.getEntities(commandContext, "targets"), BoolArgumentType.getBool(commandContext, "paused"))
                                )
                            )
                        )
                    )
                    .then(Commands.literal("hard_pause")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("paused", BoolArgumentType.bool())
                                .executes(commandContext ->
                                    hardPause(EntityArgument.getEntities(commandContext, "targets"), BoolArgumentType.getBool(commandContext, "paused"))
                                )
                            )
                        )
                    )
                )
        );
    }

    public static int playAnimation(Collection<? extends Entity> targetEntities, ResourceKey<@NonNull AnimationMontage> montageKey, String section) {
        int successEntityNum = 0;

        for (Entity entity : targetEntities) {
            EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(entity);

            if (entityPatch != null && entityPatch.getArmatureComponent() != null) {
                successEntityNum++;
                entityPatch.getArmatureComponent().getAnimator().playAnimMontage(montageKey, section);
            }
        }

        return successEntityNum;
    }

    public static int softPause(Collection<? extends Entity> targetEntities, boolean paused) {
        int successEntityNum = 0;

        for (Entity entity : targetEntities) {
            EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(entity);

            if (entityPatch != null && entityPatch.getArmatureComponent() != null) {
                successEntityNum++;
                entityPatch.getArmatureComponent().getAnimator().setSoftPause(paused);
            }
        }

        return successEntityNum;
    }

    public static int hardPause(Collection<? extends Entity> targetEntities, boolean paused) {
        int successEntityNum = 0;

        for (Entity entity : targetEntities) {
            EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(entity);

            if (entityPatch != null && entityPatch.getArmatureComponent() != null) {
                successEntityNum++;
                entityPatch.getArmatureComponent().getAnimator().setHardPause(paused);
            }
        }

        return successEntityNum;
    }
}
