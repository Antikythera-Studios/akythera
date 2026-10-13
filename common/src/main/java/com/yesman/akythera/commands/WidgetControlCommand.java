package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundClearWidgetHuds;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundOpenWidget;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.generated.LangKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/// Opens [WidgetUsage#SCREEN] and shows [WidgetUsage#HUD] [WidgetBlueprint]s on players, and clears their HUDs.
public class WidgetControlCommand {
    private static final DynamicCommandExceptionType ERROR_WIDGET = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_WIDGET_UNKNOWN_WIDGET, value)
    );
    private static final DynamicCommandExceptionType ERROR_NOT_OPENABLE = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_WIDGET_NOT_OPENABLE, value)
    );

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("widget").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("open")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("widget", ResourceKeyArgument.key(AkytheraRegistries.DataPack.WIDGET_KEY))
                                .executes(context -> {
                                    ResourceKey<WidgetBlueprint> key = ResourceKeyArgument.getRegistryKey(context, "widget", AkytheraRegistries.DataPack.WIDGET_KEY, ERROR_WIDGET);
                                    Holder.Reference<WidgetBlueprint> widget = context.getSource().registryAccess().lookupOrThrow(AkytheraRegistries.DataPack.WIDGET_KEY).get(key)
                                        .orElseThrow(() -> ERROR_WIDGET.create(key.identifier()));

                                    if (widget.value().usage() == WidgetUsage.WIDGET) throw ERROR_NOT_OPENABLE.create(widget.key().identifier());

                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                                    targets.forEach(player -> NetworkingHelper.getInstance().sendToClient(player, new ClientBoundOpenWidget(widget.key())));
                                    context.getSource().sendSuccess(
                                        () -> Component.translatable(LangKeys.COMMANDS_WIDGET_OPEN_SUCCESS, widget.key().identifier().toString(), targets.size()),
                                        true
                                    );

                                    return targets.size();
                                })
                            )
                        )
                    )
                    .then(Commands.literal("clear_huds")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(context -> {
                                Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                                targets.forEach(player -> NetworkingHelper.getInstance().sendToClient(player, ClientBoundClearWidgetHuds.INSTANCE));
                                context.getSource().sendSuccess(
                                    () -> Component.translatable(LangKeys.COMMANDS_WIDGET_CLEAR_HUDS_SUCCESS, targets.size()),
                                    true
                                );

                                return targets.size();
                            })
                        )
                    )
                )
        );
    }
}
