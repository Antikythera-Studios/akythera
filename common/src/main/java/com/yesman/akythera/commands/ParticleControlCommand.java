package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayParticleSystem;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.generated.LangKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/// Plays [ParticleSystemBlueprint]s on players' clients.
public class ParticleControlCommand {
    private static final DynamicCommandExceptionType ERROR_SYSTEM = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_PARTICLE_UNKNOWN_SYSTEM, value)
    );

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("playparticle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("system", ResourceKeyArgument.key(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY))
                        .executes(context -> play(context, new Target(context.getSource().getPosition(), null), new CompoundTag(), null))
                        .then(paramsAndViewers(context -> new Target(context.getSource().getPosition(), null)))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(context -> play(context, new Target(Vec3Argument.getVec3(context, "pos"), null), new CompoundTag(), null))
                            .then(paramsAndViewers(context -> new Target(Vec3Argument.getVec3(context, "pos"), null)))
                        )
                        .then(Commands.literal("attach")
                            .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(context -> play(context, attachedTarget(context, null), new CompoundTag(), null))
                                .then(paramsAndViewers(context -> attachedTarget(context, null)))
                                .then(Commands.argument("socket", StringArgumentType.string())
                                    .executes(context -> play(context, attachedTarget(context, StringArgumentType.getString(context, "socket")), new CompoundTag(), null))
                                    .then(paramsAndViewers(context -> attachedTarget(context, StringArgumentType.getString(context, "socket"))))
                                )
                            )
                        )
                    )
                )
        );
    }

    /// The `<params> [<viewers>]` tail, playing at the target `target` reads.
    private static ArgumentBuilder<CommandSourceStack, ?> paramsAndViewers(final TargetReader target) {
        return Commands.argument("params", CompoundTagArgument.compoundTag())
            .executes(context -> play(context, target.read(context), CompoundTagArgument.getCompoundTag(context, "params"), null))
            .then(Commands.argument("viewers", EntityArgument.players())
                .executes(context -> play(
                    context, target.read(context), CompoundTagArgument.getCompoundTag(context, "params"), EntityArgument.getPlayers(context, "viewers")
                ))
            );
    }

    /// The `attach <entity> [<socket>]` target: the entity's position, and its socket to follow. With a null
    /// `socketPath` the play stays at that position, attached to the entity for its overlay particles alone.
    private static Target attachedTarget(final CommandContext<CommandSourceStack> context, final @Nullable String socketPath) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "entity");

        return new Target(entity.position(), new ParticleAttachment(entity.getId(), ParseUtils.nvl(socketPath, ""), socketPath != null));
    }

    /// Plays the system at `target` for `viewers`, or for the players whose clients track the chunk the target's
    /// position is in when `viewers` is null. Fails without sending when the system can't be built.
    private static int play(
        final CommandContext<CommandSourceStack> context,
        final Target target,
        final CompoundTag params,
        final @Nullable Collection<ServerPlayer> viewers
    ) throws CommandSyntaxException {
        ResourceKey<ParticleSystemBlueprint> key = ResourceKeyArgument.getRegistryKey(context, "system", AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, ERROR_SYSTEM);

        context.getSource().registryAccess().lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY)
            .get(key)
            .orElseThrow(() -> ERROR_SYSTEM.create(key.identifier()));

        Collection<ServerPlayer> receivers = viewers != null
            ? viewers
            : context.getSource().getLevel().getChunkSource().chunkMap.getPlayers(ChunkPos.containing(BlockPos.containing(target.pos())), false);
        ClientBoundPlayParticleSystem payload = new ClientBoundPlayParticleSystem(key, target.pos(), params, Optional.ofNullable(target.attachment()));
        receivers.forEach(player -> NetworkingHelper.getInstance().sendToClient(player, payload));
        context.getSource().sendSuccess(
            () -> Component.translatable(LangKeys.COMMANDS_PARTICLE_PLAY_SUCCESS, key.identifier().toString(), receivers.size()),
            true
        );

        return receivers.size();
    }

    /// Where a play starts, and what it is attached to, or null for a play attached to nothing.
    private record Target(Vec3 pos, @Nullable ParticleAttachment attachment) {
    }

    @FunctionalInterface
    private interface TargetReader {
        Target read(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }
}
