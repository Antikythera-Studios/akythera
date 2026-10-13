package com.yesman.akythera.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.api.world.ability.DataDrivenAbilityCue;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.generated.LangKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/// Gives, removes and drives abilities, and plays and stops ability cues, on entities carrying an
/// [AbilitySystemComponent].
public class AbilityControlCommand {
    private static final DynamicCommandExceptionType ERROR_SLOT = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_ABILITY_UNKNOWN_SLOT, value)
    );
    private static final DynamicCommandExceptionType ERROR_ABILITY = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_ABILITY_UNKNOWN_ABILITY, value)
    );
    private static final DynamicCommandExceptionType ERROR_CUE = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_ABILITY_UNKNOWN_CUE, value)
    );
    private static final DynamicCommandExceptionType ERROR_CUE_SCRIPT = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_ABILITY_UNKNOWN_CUE_SCRIPT, value)
    );
    private static final DynamicCommandExceptionType ERROR_TAG = new DynamicCommandExceptionType(
        value -> Component.translatable(LangKeys.COMMANDS_ABILITY_UNKNOWN_TAG, value)
    );

    private static final SuggestionProvider<CommandSourceStack> SLOT_SUGGESTIONS = suggestDataBlocks(AbilitySlot.ENUM_MANAGER);
    private static final SuggestionProvider<CommandSourceStack> TAG_SUGGESTIONS = suggestDataBlocks(AbilityTag.ENUM_MANAGER);

    /// Every registered [ScriptType] that plays as a Java cue.
    private static final SuggestionProvider<CommandSourceStack> CUE_SCRIPT_SUGGESTIONS = (context, builder) ->
        SharedSuggestionProvider.suggestResource(
            AkytheraRegistries.Frozen.SCRIPT_TYPE.entrySet().stream()
                .filter(entry -> AbilityCueScript.runnable(entry.getValue().type()))
                .map(entry -> entry.getKey().identifier()),
            builder
        );

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal(Akythera.MODID)
                .then(Commands.literal("ability").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .then(Commands.argument("slot", IdentifierArgument.id()).suggests(SLOT_SUGGESTIONS)
                                .then(Commands.argument("ability", ResourceKeyArgument.key(AkytheraRegistries.DataPack.ABILITY_KEY))
                                    .executes(context -> {
                                        AbilitySlot slot = getSlot(context);
                                        Holder.Reference<AbilityBlueprint> blueprint = getHolder(
                                            context, "ability", AkytheraRegistries.DataPack.ABILITY_KEY, ERROR_ABILITY
                                        );

                                        return report(
                                            context.getSource(),
                                            forEachComponent(
                                                EntityArgument.getEntities(context, "targets"),
                                                component -> {
                                                    Ability<?> ability = AbilityBlueprint.createAbility(blueprint);

                                                    return ability != null && component.giveAbility(slot, ability) != null;
                                                }
                                            ),
                                            LangKeys.COMMANDS_ABILITY_GIVE_SUCCESS,
                                            LangKeys.COMMANDS_ABILITY_GIVE_FAILED,
                                            blueprint.key().identifier(),
                                            IdentifierArgument.getId(context, "slot")
                                        );
                                    })
                                )
                            )
                        )
                    )
                    .then(slotAction(
                        "remove",
                        AbilitySystemComponent::removeAbility,
                        LangKeys.COMMANDS_ABILITY_REMOVE_SUCCESS,
                        LangKeys.COMMANDS_ABILITY_REMOVE_FAILED
                    ))
                    .then(slotAction(
                        "activate",
                        AbilitySystemComponent::activateAbility,
                        LangKeys.COMMANDS_ABILITY_ACTIVATE_SUCCESS,
                        LangKeys.COMMANDS_ABILITY_ACTIVATE_FAILED
                    ))
                    .then(slotAction(
                        "cancel",
                        AbilitySystemComponent::cancelAbility,
                        LangKeys.COMMANDS_ABILITY_CANCEL_SUCCESS,
                        LangKeys.COMMANDS_ABILITY_CANCEL_FAILED
                    ))
                    .then(slotAction(
                        "end",
                        AbilitySystemComponent::endAbility,
                        LangKeys.COMMANDS_ABILITY_END_SUCCESS,
                        LangKeys.COMMANDS_ABILITY_END_FAILED
                    ))
                    .then(Commands.literal("cue")
                        .then(Commands.literal("play")
                            .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("data")
                                    .then(Commands.argument("cue", ResourceKeyArgument.key(AkytheraRegistries.DataPack.ABILITY_CUE_KEY))
                                        .executes(context -> {
                                            Holder.Reference<AbilityCueBlueprint> cue = getHolder(
                                                context, "cue", AkytheraRegistries.DataPack.ABILITY_CUE_KEY, ERROR_CUE
                                            );

                                            return report(
                                                context.getSource(),
                                                forEachComponent(
                                                    EntityArgument.getEntities(context, "targets"),
                                                    component -> component.playCue(cue.key(), null, false, false) != null
                                                ),
                                                LangKeys.COMMANDS_ABILITY_CUE_PLAY_SUCCESS,
                                                LangKeys.COMMANDS_ABILITY_CUE_PLAY_FAILED,
                                                cue.key().identifier()
                                            );
                                        })
                                    )
                                )
                                .then(Commands.literal("code")
                                    .then(Commands.argument("script", IdentifierArgument.id()).suggests(CUE_SCRIPT_SUGGESTIONS)
                                        .executes(context -> {
                                            Class<? extends AbilityCueScript> script = getCueScript(context);

                                            return report(
                                                context.getSource(),
                                                forEachComponent(
                                                    EntityArgument.getEntities(context, "targets"),
                                                    component -> component.playCue(script, null, false, false) != null
                                                ),
                                                LangKeys.COMMANDS_ABILITY_CUE_PLAY_SUCCESS,
                                                LangKeys.COMMANDS_ABILITY_CUE_PLAY_FAILED,
                                                IdentifierArgument.getId(context, "script")
                                            );
                                        })
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("stop")
                            .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("data")
                                    .then(Commands.argument("cue", ResourceKeyArgument.key(AkytheraRegistries.DataPack.ABILITY_CUE_KEY))
                                        .executes(context -> {
                                            ResourceKey<AbilityCueBlueprint> cueKey = ResourceKeyArgument.getRegistryKey(
                                                context, "cue", AkytheraRegistries.DataPack.ABILITY_CUE_KEY, ERROR_CUE
                                            );

                                            return report(
                                                context.getSource(),
                                                forEachComponent(
                                                    EntityArgument.getEntities(context, "targets"),
                                                    component -> component.cancelCues(
                                                        cue -> cue.getScript() instanceof DataDrivenAbilityCue dataDriven
                                                            && dataDriven.getBlueprint().is(cueKey)
                                                    ) > 0
                                                ),
                                                LangKeys.COMMANDS_ABILITY_CUE_STOP_SUCCESS,
                                                LangKeys.COMMANDS_ABILITY_CUE_STOP_FAILED,
                                                cueKey.identifier()
                                            );
                                        })
                                    )
                                )
                                .then(Commands.literal("code")
                                    .then(Commands.argument("script", IdentifierArgument.id()).suggests(CUE_SCRIPT_SUGGESTIONS)
                                        .executes(context -> {
                                            Class<? extends AbilityCueScript> script = getCueScript(context);

                                            return report(
                                                context.getSource(),
                                                forEachComponent(
                                                    EntityArgument.getEntities(context, "targets"),
                                                    component -> component.cancelCues(cue -> cue.getScript().getClass() == script) > 0
                                                ),
                                                LangKeys.COMMANDS_ABILITY_CUE_STOP_SUCCESS,
                                                LangKeys.COMMANDS_ABILITY_CUE_STOP_FAILED,
                                                IdentifierArgument.getId(context, "script")
                                            );
                                        })
                                    )
                                )
                                .then(Commands.literal("tag")
                                    .then(Commands.argument("tag", IdentifierArgument.id()).suggests(TAG_SUGGESTIONS)
                                        .executes(context -> {
                                            String path = IdentifierArgument.getId(context, "tag").getPath();
                                            AbilityTag tag = AbilityTag.ENUM_MANAGER.universalValues().stream()
                                                .filter(candidate -> candidate.path().equals(path))
                                                .findFirst()
                                                .orElseThrow(() -> ERROR_TAG.create(path));

                                            return report(
                                                context.getSource(),
                                                forEachComponent(
                                                    EntityArgument.getEntities(context, "targets"),
                                                    component -> component.cancelCue(tag) > 0
                                                ),
                                                LangKeys.COMMANDS_ABILITY_CUE_STOP_SUCCESS,
                                                LangKeys.COMMANDS_ABILITY_CUE_STOP_FAILED,
                                                path
                                            );
                                        })
                                    )
                                )
                            )
                        )
                    )
                )
        );
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// `/akythera ability <literal> <targets> <slot>`, running `action` on each target's slot.
    private static LiteralArgumentBuilder<CommandSourceStack> slotAction(
        final String literal,
        final BiPredicate<AbilitySystemComponent, AbilitySlot> action,
        final String successKey,
        final String failedKey
    ) {
        return Commands.literal(literal)
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("slot", IdentifierArgument.id()).suggests(SLOT_SUGGESTIONS)
                    .executes(context -> {
                        AbilitySlot slot = getSlot(context);

                        return report(
                            context.getSource(),
                            forEachComponent(
                                EntityArgument.getEntities(context, "targets"),
                                component -> action.test(component, slot)
                            ),
                            successKey,
                            failedKey,
                            IdentifierArgument.getId(context, "slot")
                        );
                    })
                )
            );
    }

    /// Suggests the identifier of every entry `manager` holds.
    private static SuggestionProvider<CommandSourceStack> suggestDataBlocks(
        final EnumerableDataBlockManager<? extends EnumerableDataBlock> manager
    ) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
            manager.universalValues().stream().map(manager::derefIdentifier).filter(Objects::nonNull),
            builder
        );
    }

    /// The [AbilitySlot] the `slot` argument names.
    private static AbilitySlot getSlot(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier identifier = IdentifierArgument.getId(context, "slot");

        return AbilitySlot.ENUM_MANAGER.get(identifier).orElseThrow(() -> ERROR_SLOT.create(identifier));
    }

    /// The registry entry the `name` argument names.
    private static <T> Holder.Reference<T> getHolder(
        final CommandContext<CommandSourceStack> context,
        final String name,
        final ResourceKey<Registry<T>> registryKey,
        final DynamicCommandExceptionType error
    ) throws CommandSyntaxException {
        ResourceKey<T> key = ResourceKeyArgument.getRegistryKey(context, name, registryKey, error);

        return context.getSource().registryAccess().lookupOrThrow(registryKey).get(key)
            .orElseThrow(() -> error.create(key.identifier()));
    }

    /// The Java cue the `script` argument names by its [ScriptType] id.
    private static Class<? extends AbilityCueScript> getCueScript(
        final CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        Identifier identifier = IdentifierArgument.getId(context, "script");
        ScriptType scriptType = AkytheraRegistries.Frozen.SCRIPT_TYPE.getValue(identifier);

        if (scriptType == null || !AbilityCueScript.runnable(scriptType.type())) throw ERROR_CUE_SCRIPT.create(identifier);

        return scriptType.type().asSubclass(AbilityCueScript.class);
    }

    /// Runs `action` against every target that carries an [AbilitySystemComponent].
    ///
    /// @return how many targets the action reported success for
    private static int forEachComponent(
        final Collection<? extends Entity> targets,
        final Predicate<AbilitySystemComponent> action
    ) {
        int count = 0;

        for (Entity entity : targets) {
            EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(entity);

            if (entityPatch == null) continue;

            AbilitySystemComponent component = entityPatch.getComponent(AbilitySystemComponent.class);

            if (component == null) continue;

            if (action.test(component)) count++;
        }

        return count;
    }

    /// Sends `successKey` with `args` and `count` appended, or `failedKey` with `args` when `count` is zero.
    /// Each of `args` is written as its string.
    private static int report(
        final CommandSourceStack source,
        final int count,
        final String successKey,
        final String failedKey,
        final Object... args
    ) {
        Object[] texts = new Object[args.length + 1];

        for (int i = 0; i < args.length; i++) {
            texts[i] = String.valueOf(args[i]);
        }

        texts[args.length] = count;

        if (count == 0) {
            source.sendFailure(Component.translatable(failedKey, Arrays.copyOf(texts, args.length)));
        } else {
            source.sendSuccess(() -> Component.translatable(successKey, texts), true);
        }

        return count;
    }
}
