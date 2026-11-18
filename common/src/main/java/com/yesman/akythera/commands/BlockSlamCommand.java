package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.yesman.akythera.api.world.BlockSlam;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.generated.LangKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/// `/akythera blockslam <pos> <radius>`: plays a [BlockSlam] for the players tracking the chunk.
public class BlockSlamCommand {
    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("blockslam").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(BlockSlam.MIN_RADIUS))
                            .executes(BlockSlamCommand::slam)
                        )
                    )
                )
        );
    }

    private static int slam(final CommandContext<CommandSourceStack> context) {
        Vec3 pos = Vec3Argument.getVec3(context, "pos");

        if (!BlockSlam.slam(context.getSource().getLevel(), pos, DoubleArgumentType.getDouble(context, "radius"))) {
            context.getSource().sendFailure(Component.translatable(LangKeys.COMMANDS_BLOCK_SLAM_FAILED, pos.toString()));

            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMANDS_BLOCK_SLAM_SUCCESS, pos.toString()), true);

        return 1;
    }
}
