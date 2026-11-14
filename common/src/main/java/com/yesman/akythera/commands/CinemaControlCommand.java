package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.yesman.akythera.api.cinema.CinemaScenePlayer;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.generated.LangKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/// Plays [CinemaScene]s.
///
/// ```
/// /akythera cinema play <scene> at <pos> <yRot> <role> <entity> [<role> <entity>]...
/// /akythera cinema play <scene> on <root> <role> <entity> [<role> <entity>]...
/// ```
public class CinemaControlCommand {
    /// The most `<role> <entity>` pairs one command takes.
    private static final int MAX_ACTORS = 6;

    private static final DynamicCommandExceptionType ERROR_SCENE = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_CINEMA_UNKNOWN_SCENE, value)
    );

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("cinema").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("play")
                        .then(Commands.argument("scene", ResourceKeyArgument.key(AkytheraRegistries.DataPack.CINEMA_SCENE_KEY))
                            .then(Commands.literal("at")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                    .then(Commands.argument("yRot", FloatArgumentType.floatArg())
                                        .then(actors(1, (context, player) ->
                                            player.playAt(Vec3Argument.getVec3(context, "pos"), FloatArgumentType.getFloat(context, "yRot"))
                                        ))
                                    )
                                )
                            )
                            .then(Commands.literal("on")
                                .then(Commands.argument("root", EntityArgument.entity())
                                    .then(actors(1, (context, player) -> player.playOn(EntityArgument.getEntity(context, "root"))))
                                )
                            )
                        )
                    )
                )
        );
    }

    /// The `<role> <entity>` pair `index`, counted from 1, followed by the pairs after it. Each pair may end the
    /// command.
    private static ArgumentBuilder<CommandSourceStack, ?> actors(final int index, final Root root) {
        RequiredArgumentBuilder<CommandSourceStack, ?> entity = Commands.argument("entity" + index, EntityArgument.entity())
            .executes(context -> play(context, root, index));

        if (index < MAX_ACTORS) entity.then(actors(index + 1, root));

        return Commands.argument("role" + index, StringArgumentType.string()).then(entity);
    }

    /// Plays the scene with the first `actorCount` pairs cast.
    private static int play(final CommandContext<CommandSourceStack> context, final Root root, final int actorCount) throws CommandSyntaxException {
        ResourceKey<CinemaScene> key = ResourceKeyArgument.getRegistryKey(context, "scene", AkytheraRegistries.DataPack.CINEMA_SCENE_KEY, ERROR_SCENE);
        CinemaScenePlayer player = CinemaScenePlayer.of(
            context.getSource().registryAccess().lookupOrThrow(AkytheraRegistries.DataPack.CINEMA_SCENE_KEY)
                .getOptional(key)
                .orElseThrow(() -> ERROR_SCENE.create(key.identifier()))
        );

        for (int index = 1; index <= actorCount; index++) {
            EntityPatch<?> patch = AkytheraPatches.getEntityPatch(EntityArgument.getEntity(context, "entity" + index));

            if (patch != null) player.actor(StringArgumentType.getString(context, "role" + index), patch);
        }

        root.play(context, player);
        context.getSource().sendSuccess(
            () -> Component.translatable(LangKeys.COMMANDS_CINEMA_PLAY_SUCCESS, key.identifier().toString(), actorCount),
            true
        );

        return actorCount;
    }

    /// Plays a cast scene around the root the command names.
    @FunctionalInterface
    private interface Root {
        void play(CommandContext<CommandSourceStack> context, CinemaScenePlayer player) throws CommandSyntaxException;
    }
}
